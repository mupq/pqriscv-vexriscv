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

case class PipelinedMemoryBusXilinxRam(size : BigInt, latency : Int = 1) extends Component{
  require(size % 4 == 0, "Size must be multiple of 4 bytes")
  require(size > 0, "Size must be greater than zero")
  val busConfig = PipelinedMemoryBusConfig(log2Up(size), 32)
  val io = new Bundle{
    val bus = slave(PipelinedMemoryBus(busConfig))
  }

  val ram = new XilinxSinglePortRAM(
    dataWidth = 32,
    numWords = size / 4,
    readLatency = latency,
    byteWrite = true)

  ram.io.dina := io.bus.cmd.data
  ram.io.addra := io.bus.cmd.address.asBits >> 2
  ram.io.ena := io.bus.cmd.valid
  ram.io.wea := io.bus.cmd.write ? io.bus.cmd.mask | B"0000"
  ram.io.regcea := True
  ram.io.injectdbiterra := False
  ram.io.injectsbiterra := False
  ram.io.sleep := False
  io.bus.cmd.ready := True

  io.bus.rsp.valid := Delay(io.bus.cmd.fire && !io.bus.cmd.write, latency, init = False)
  io.bus.rsp.data := ram.io.douta
}


class PQVexRiscvArty(
  val ramBlockSizes : Seq[BigInt] = Seq[BigInt](64 KiB, 64 KiB),
  val clkFrequency : HertzNumber = 100 MHz,
  val coreFrequency : HertzNumber = 50 MHz,
  cpuPlugins : () => Seq[Plugin[VexRiscv]] = PQVexRiscv.withDSPMultiplier()
) extends PQVexRiscv (
  cpuPlugins = cpuPlugins,
  ibusRange = SizeMapping(0x80000000l, ramBlockSizes.reduce(_ + _))
) {
  val io = new Bundle {
    val RST = in Bool
    val CLK = in Bool
    /* UART */
    val TXD = out Bool // TXD
    val RXD = in Bool // RXD
    /* JTAG */
    val TDO = out Bool // TDO
    val TCK = in Bool // TCK
    val TDI = in Bool // TDI
    val TMS = in Bool // TMS
  }
  noIoPrefix()

  if (clkFrequency == coreFrequency) {
    asyncReset := !io.RST
    mainClock := io.CLK
  } else {
    val pll = new XilinxPLLBase(clkFrequency, Array(coreFrequency))
    pll.io.CLKIN1 := io.CLK
    pll.io.RST := !io.RST
    pll.io.CLKFBIN := pll.io.CLKFBOUT
    pll.io.PWRDWN := False

    val bufg = new XilinxGlobalBuffer()
    bufg.io.I := pll.io.CLKOUT0

    asyncReset := !io.RST && !pll.io.LOCKED
    mainClock := bufg.io.O
  }

  io.TDO := jtag.tdo
  jtag.tck := io.TCK
  jtag.tdi := io.TDI
  jtag.tms := io.TMS

  uart.rxd := io.RXD
  io.TXD := uart.txd

  val memory = new ClockingArea(systemClockDomain) {
    val ramBlocks = ramBlockSizes.zipWithIndex.map(t => PipelinedMemoryBusXilinxRam(t._1, 2))
    var curAddr : BigInt = 0x80000000l
    for (block <- ramBlocks) {
      busSlaves += block.io.bus -> SizeMapping(curAddr, block.size)
      curAddr += block.size
    }
  }
}

object PQVexRiscvArty {
  def main(args: Array[String]) : Unit = {
    case class PQVexRiscvArtyConfig(
      ramBlocks: Seq[BigInt] = Seq(64 KiB, 64 KiB),
      clkFrequency: HertzNumber = 100 MHz,
      coreFrequency: HertzNumber = 50 MHz,
      cpuPlugins: () => Seq[Plugin[VexRiscv]] = PQVexRiscv.baseConfig()
    )
    val optParser = new OptionParser[PQVexRiscvArtyConfig]("PQVexRiscvArty") {
      head("PQVexRiscvArty board")
      help("help") text("print usage text")
      opt[Seq[Int]]("ram") action((r, c) => c.copy(ramBlocks = r.map(_ KiB))) text("SRAM Blocks in KiB") valueName("<block1>,<block2>")
      opt[Int]("clk") action((r, c) => c.copy(clkFrequency = (r MHz))) text("Input clock freqency in MHz") valueName("<freq>")
      opt[Int]("core") action((r, c) => c.copy(coreFrequency = (r MHz))) text("Target core freqency in MHz") valueName("<freq>")
      opt[Unit]("mul") action((_, c) => c.copy(cpuPlugins = PQVexRiscv.withDSPMultiplier(c.cpuPlugins)))
    }
    val config = optParser.parse(args, PQVexRiscvArtyConfig()) match {
      case Some(config) => config
      case None => ???
    }
    SpinalConfig(
      mode = Verilog,
      targetDirectory = "rtl"
    ).generate(new PQVexRiscvArty(ramBlockSizes = config.ramBlocks, clkFrequency = config.clkFrequency, coreFrequency = config.coreFrequency, cpuPlugins = config.cpuPlugins))
  }
}
