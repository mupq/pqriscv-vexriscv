package mupq

import java.io.{File, FileInputStream, FileOutputStream, IOException, OutputStream}

import scopt.OptionParser

import spinal.sim._
import spinal.core._
import spinal.lib._
import spinal.core.sim._

import spinal.lib.bus.simple._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.io.TriStateArray
import spinal.lib.com.jtag.Jtag
import spinal.lib.com.uart.Uart
import spinal.lib.com.jtag.sim.JtagTcp

import vexriscv.VexRiscv
import vexriscv.plugin.Plugin

case class PipelinedMemoryBusRam(size: BigInt, initialContent: File = null) extends Component {
  require(size % 4 == 0, "Size must be multiple of 4 bytes")
  require(size > 0, "Size must be greater than zero")
  val busConfig = PipelinedMemoryBusConfig(log2Up(size), 32)
  val io = new Bundle {
    val bus = slave(PipelinedMemoryBus(busConfig))
  }

  val ram = Mem(Bits(32 bits), size / 4)
  io.bus.rsp.valid := Delay(io.bus.cmd.fire && !io.bus.cmd.write, 2, init = False)
  val rdata = ram.readWriteSync(
    address = io.bus.cmd.address >> 2,
    data = io.bus.cmd.data,
    enable = io.bus.cmd.valid,
    write = io.bus.cmd.write,
    mask = io.bus.cmd.mask
  )
  io.bus.rsp.data := RegNext(rdata) init (0)
  io.bus.cmd.ready := True

  if (initialContent != null) {
    val input       = new FileInputStream(initialContent)
    val initContent = Array.fill[BigInt](ram.wordCount)(0)
    val fileContent = Array.ofDim[Byte](Seq(input.available, initContent.length * 4).min)
    input.read(fileContent)
    for ((byte, addr) <- fileContent.zipWithIndex) {
      val l = java.lang.Byte.toUnsignedLong(byte) << ((addr & 3) * 8)
      initContent(addr >> 2) |= BigInt(l)
    }
    ram.initBigInt(initContent)
  }
}

class PQVexRiscvSim(
  val ramBlockSizes: Seq[BigInt] = Seq[BigInt](256 KiB, 128 KiB),
  val initialContent: File = null,
  val coreFrequency: HertzNumber = 12 MHz,
  cpuPlugins: () => Seq[Plugin[VexRiscv]] = PQVexRiscv.withDSPMultiplier()
)
extends PQVexRiscv(
  cpuPlugins = cpuPlugins,
  ibusRange = SizeMapping(0x80000000L, ramBlockSizes.reduce(_ + _))
) {
  val io = new Bundle {
    val asyncReset = in Bool
    val mainClock  = in Bool
    val uart       = master(Uart())
    val jtag       = slave(Jtag())
  }

  asyncReset := io.asyncReset
  mainClock := io.mainClock

  uart <> io.uart
  jtag <> io.jtag

  val memory = new ClockingArea(systemClockDomain) {
    val ramBlocks =
      ramBlockSizes.zipWithIndex.map(t =>
        PipelinedMemoryBusRam(t._1, if (t._2 == 0) initialContent else null))
    var curAddr: BigInt = 0x80000000L
    for (block <- ramBlocks) {
      busSlaves += block.io.bus -> SizeMapping(curAddr, block.size)
      curAddr += block.size
    }
  }
}

object PQVexRiscvSim {

  def main(args: Array[String]) = {
    case class PQVexRiscvSimConfig(
      uartOutFile: OutputStream = System.out,
      initFile: File = null,
      ramBlocks: Seq[BigInt] = Seq(256 KiB, 128 KiB),
      cpuPlugins: () => Seq[Plugin[VexRiscv]] = PQVexRiscv.withDSPMultiplier()
    )
    val optParser = new OptionParser[PQVexRiscvSimConfig]("PQVexRiscvSim") {
      head("PQVexRiscvSim simulator")
      help("help") text ("print usage text")
      opt[File]("uart") action ((f, c) =>
        c.copy(uartOutFile =
          new FileOutputStream(
            f,
            true))) text ("File for UART output (will be appended)") valueName ("<output>")
      opt[File]("init") action ((f, c) =>
        c.copy(initFile = f)) text ("Initialization file for first RAM block") valueName ("<bin>")
      opt[Seq[Int]]("ram") action ((r, c) =>
        c.copy(ramBlocks =
          r.map(_ KiB))) text ("SRAM Blocks in KiB") valueName ("<block1>,<block2>")
    }

    val config = optParser.parse(args, PQVexRiscvSimConfig()) match {
      case Some(config) => config
      case None         => ???
    }

    val compiled = SimConfig.allOptimisation.compile {
      new PQVexRiscvSim(
        config.ramBlocks,
        config.initFile,
        cpuPlugins = config.cpuPlugins
      )
    }

    compiled.doSim("PqVexRiscvSim", 42) { dut =>
      val mainClkPeriod  = (1e12 / dut.coreFrequency.toDouble).toLong
      val jtagClkPeriod  = mainClkPeriod * 4
      val uartBaudRate   = 115200
      val uartBaudPeriod = (1e12 / uartBaudRate.toDouble).toLong

      val clockDomain = ClockDomain(dut.io.mainClock, dut.io.asyncReset)
      clockDomain.forkStimulus(mainClkPeriod)

      val tcpJtag = JtagTcp(
        jtag = dut.io.jtag,
        jtagClkPeriod = jtagClkPeriod
      )

      println(s"Simulating ${dut.getClass.getName} with JtagTcp on port 7894")

      val uartPin = dut.io.uart.txd

      val uartDecoder = fork {
        sleep(1)
        waitUntil(uartPin.toBoolean == true)
        try {
          while (true) {
            waitUntil(uartPin.toBoolean == false)
            sleep(uartBaudPeriod / 2)
            if (uartPin.toBoolean != false) {
              println("\rUART frame error (start bit)")
            } else {
              sleep(uartBaudPeriod)
              var byte = 0
              var i    = 0
              while (i < 8) {
                if (uartPin.toBoolean) {
                  byte |= 1 << i
                }
                sleep(uartBaudPeriod)
                i += 1
              }
              if (uartPin.toBoolean) {
                config.uartOutFile.write(byte)
              } else {
                println("\rUART frame error (stop bit)")
              }
            }
          }
        } catch {
          case io: IOException =>
        }
        println("\rUART decoder stopped")
      }

      var running = true

      while (running) {
        sleep(mainClkPeriod * 50000)
      }
    }
  }

}
