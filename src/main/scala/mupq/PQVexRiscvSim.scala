package mupq

import java.io.{File, FileOutputStream, IOException, OutputStream}

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

case class PipelinedMemoryBusRam(size : BigInt) extends Component{
  require(size % 4 == 0, "Size must be multiple of 4 bytes")
  require(size > 0, "Size must be greater than zero")
  val busConfig = PipelinedMemoryBusConfig(log2Up(size), 32)
  val io = new Bundle{
    val bus = slave(PipelinedMemoryBus(busConfig))
  }

  val ram = Mem(Bits(32 bits), size / 4)
  io.bus.rsp.valid := RegNext(io.bus.cmd.fire && !io.bus.cmd.write) init(False)
  io.bus.rsp.data := ram.readWriteSync(
    address = (io.bus.cmd.address >> 2).resized,
    data  = io.bus.cmd.data,
    enable  = io.bus.cmd.valid,
    write  = io.bus.cmd.write,
    mask  = io.bus.cmd.mask
  )
  io.bus.cmd.ready := True
}




class PQVexRiscvSim(
  val ramBlockSizes : Seq[BigInt] = Seq[BigInt](256 KiB, 128 KiB),
  val coreFrequency : HertzNumber = 12 MHz
) extends PQVexRiscv(
  cpuPlugins = PQVexRiscv.defaultPlugins,
  ibusRange = SizeMapping(0x80000000l, (256 + 128) KiB)
) {
  val io = new Bundle {
    val asyncReset = in Bool
    val mainClock = in Bool
    val uart = master(Uart())
    val jtag = slave(Jtag())
  }

  asyncReset := io.asyncReset
  mainClock := io.mainClock

  uart <> io.uart
  jtag <> io.jtag

  val memory = new ClockingArea(systemClockDomain) {
    val ramBlocks = ramBlockSizes.map(PipelinedMemoryBusRam(_))
    var curAddr : BigInt = 0x80000000l
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
      ramBlocks: Seq[BigInt] = Seq(256, 128)
    )
    val optParser = new OptionParser[PQVexRiscvSimConfig]("PQVexRiscvSim") {
      head("PQVexRiscvSim simulator")
      opt[File]("uart") action((f, c) => c.copy(uartOutFile = new FileOutputStream(f, true))) text("File for UART output (will be appended)") valueName("<output>")
      opt[Seq[Int]]("ram") action((r, c) => c.copy(ramBlocks = r.map(_ KiB))) text("SRAM Blocks in KiB") valueName("<block1>,<block2>")
    }

    val config = optParser.parse(args, PQVexRiscvSimConfig()) match {
      case Some(config) => config
      case None => ???
    }

    val compiled = SimConfig.allOptimisation.compile {
      new PQVexRiscvSim(config.ramBlocks)
    }

    compiled.doSim("PqVexRiscvSim", 42) { dut =>
      val mainClkPeriod = (1e12 / (12 MHz).toDouble).toLong
      val jtagClkPeriod = mainClkPeriod * 4
      val uartBaudRate = 115200
      val uartBaudPeriod = (1e12 / uartBaudRate).toLong

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
              var i = 0
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
