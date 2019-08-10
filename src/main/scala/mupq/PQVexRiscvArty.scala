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

class PQVexRiscvArty(
  val ramBlockSizes : Seq[BigInt] = Seq[BigInt](64 KiB, 64 KiB),
  val initialContent : File = null,
  val coreFrequency : HertzNumber = 50 MHz,
  cpuPlugins : Seq[Plugin[VexRiscv]] = PQVexRiscv.defaultPlugins
) extends PQVexRiscv(
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

  val pll = new XilinxPLLBase(100 MHz, Array(50 MHz))
  pll.io.CLKIN1 := io.CLK
  pll.io.RST := !io.RST
  pll.io.CLKFBIN := pll.io.CLKFBOUT
  pll.io.PWRDWN := False

  val bufg = new XilinxGlobalBuffer()
  bufg.io.I := pll.io.CLKOUT0

  asyncReset := !io.RST && !pll.io.LOCKED
  mainClock := bufg.io.O

  io.TDO := jtag.tdo
  jtag.tck := io.TCK
  jtag.tdi := io.TDI
  jtag.tms := io.TMS

  uart.rxd := io.RXD
  io.TXD := uart.txd

  val memory = new ClockingArea(systemClockDomain) {
    val ramBlocks = ramBlockSizes.zipWithIndex.map(t => PipelinedMemoryBusRam(t._1, if (t._2 == 0) initialContent else null))
    var curAddr : BigInt = 0x80000000l
    for (block <- ramBlocks) {
      busSlaves += block.io.bus -> SizeMapping(curAddr, block.size)
      curAddr += block.size
    }
  }
}

object PQVexRiscvArty {
  def main(args: Array[String]) : Unit = {
    SpinalConfig(
      mode = Verilog,
      targetDirectory = "rtl"
    ).generate(new PQVexRiscvArty)
  }
}
