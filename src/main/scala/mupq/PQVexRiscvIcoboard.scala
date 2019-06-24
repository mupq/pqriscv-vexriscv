package mupq

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.io.{TriStateArray, InOutWrapper}
import spinal.lib.bus.misc._
import spinal.lib.bus.simple._

import vexriscv._
import vexriscv.plugin._

case class GenericSRAM(addrWidth : Int = 16, dataWidth : Int = 16) extends Bundle with IMasterSlave {
  val selWidth: Int = (dataWidth / 8)
  val addr = Bits(addrWidth bits)
  val dataOut = Bits(dataWidth bits)
  val dataIn = Bits(dataWidth bits)
  val ce_n = Bool /* ~ChipEnable */
  val oe_n = Bool /* ~OutputEnable */
  val we_n = Bool /* ~WriteEnable */
  val sel_n = Bits(selWidth bits) /* ~Byte Control */

  override def asMaster() : Unit = {
    out(addr,dataOut, ce_n, oe_n, we_n, sel_n)
    in(dataIn)
  }
}

class PipelinedMemoryBusSRAM(busConfig: PipelinedMemoryBusConfig) extends Component {
  require(busConfig.dataWidth == 32, "Only 32 bit busses supported")
  val io = new Bundle {
    val bus = slave(PipelinedMemoryBus(busConfig))
    val clk90deg = in Bool
    val sram = master(GenericSRAM(19, 16))
  }

  val addr = Reg(Bits(io.sram.addrWidth bit))
  val dataOut = Reg(Bits(16 bit))
  val dataIn = Bits(16 bit)
  val we_n = RegNext(True) init(true)
  val selAllSet = B(2 bits, default -> true)
  val sel_n = Reg(selAllSet) init(selAllSet)

  io.sram.addr := addr
  dataIn := io.sram.dataIn
  io.sram.dataOut := dataOut
  io.sram.ce_n := RegNext(False) init(true)
  io.sram.we_n := we_n || !io.clk90deg
  io.sram.oe_n := !we_n
  io.sram.sel_n := sel_n

  val fsm = new StateMachine() {
    val cmd = RegNextWhen(io.bus.cmd.payload, io.bus.cmd.fire)
    io.bus.cmd.ready := False

    val data = Reg(Bits(32 bit))
    val valid = RegNext(False) init(false)
    io.bus.rsp.data := data
    io.bus.rsp.valid := valid

    addr.assignDontCare()
    dataOut.assignDontCare()

    val IDLE : State = new State with EntryPoint
    IDLE.whenIsActive {
      io.bus.cmd.ready := True
      when(io.bus.cmd.valid) {
        addr := io.bus.cmd.address(io.sram.addrWidth downto 2).asBits ## False
        dataOut := io.bus.cmd.data(15 downto 0)
        we_n := !io.bus.cmd.write
        sel_n := ~io.bus.cmd.mask(1 downto 0)
        goto(LOWER)
      }
    }

    val LOWER = new State
    LOWER.whenIsActive {
      data(15 downto 0) := dataIn
      /* Setup read/write for upper byte */
      addr := cmd.address(io.sram.addrWidth downto 2).asBits ## True
      dataOut := cmd.data(31 downto 16)
      we_n := !cmd.write
      sel_n := ~cmd.mask(3 downto 2)
      goto(UPPER)
    }

    val UPPER = new State
    UPPER.whenIsActive {
      data(31 downto 16) := dataIn
      valid := !cmd.write
      goto(IDLE)
    }
  }
}

class PQVexRiscvIcoboard(
  val coreFrequency : HertzNumber = 20 MHz
) extends PQVexRiscv(
  cpuPlugins = PQVexRiscv.defaultPlugins,
  ibusRange = SizeMapping(0x80000000l, 1 MiB),
  gpioWidth = 3,
  genTimer = true
) {
  val io = new Bundle {

    val clk_100mhz = in Bool
    // LED's
    val led1 = out Bool
    val led2 = out Bool
    val led3 = out Bool
    /* UART */
    val pmod3_8 = out Bool // TXD
    val pmod3_9 = in Bool // RXD
    /* JTAG */
    val pmod4_1 = out Bool // TDO
    val pmod4_3 = in Bool // TCK
    val pmod4_7 = in Bool // TDI
    val pmod4_8 = in Bool // TMS
    /* SRAM */
    val sram_addr = out Bits(19 bits)
    // val sram_data = inout(Analog(Vec(Bool, 16)))
    val sram_data = master(TriStateArray(16 bits))
    val sram_ce_n = out Bool
    val sram_oe_n = out Bool
    val sram_we_n = out Bool
    val sram_lb_n = out Bool
    val sram_ub_n = out Bool
  }
  /* Remove io_ prefix from generated Verilog */
  noIoPrefix()
  /* PLL config 100mhz -> 20mhz (using icepll tool of icestorm) */
  val pll = new Ice40PLL2FCore(
    divR = 4,
    divF = 0,
    divQ = 5)

  pll.io.REFERENCECLK := io.clk_100mhz
  pll.io.BYPASS := False
  pll.io.RESETB := True

  val clk_20mhz = Bool
  val clk_20mhz_90deg = Bool
  clk_20mhz := pll.io.PLLOUTGLOBALA
  clk_20mhz_90deg := pll.io.PLLOUTGLOBALB

  asyncReset := !pll.io.LOCK
  mainClock := pll.io.PLLOUTGLOBALA

  jtag.tdo <> io.pmod4_1
  jtag.tck <> io.pmod4_3
  jtag.tdi <> io.pmod4_7
  jtag.tms <> io.pmod4_8

  uart.rxd <> io.pmod3_9
  uart.txd <> io.pmod3_8

  val memory = new ClockingArea(systemClockDomain) {
    val ram = new PipelinedMemoryBusSRAM(busConfig)
    busSlaves += ram.io.bus -> SizeMapping(0x80000000l, 1 MiB)
    // val ice40io : Seq[Ice40IO] = (0 to 15).map(_ => new Ice40IO(0x29))
    // ice40io.zipWithIndex.foreach { case (tio, i) =>
    //   tio.io.D_OUT_0 := ram.io.sram.dataOut(i)
    //   tio.io.OUTPUT_ENABLE := !ram.io.sram.we_n
    //   ram.io.sram.dataIn(i) := tio.io.D_IN_0
    //   tio.io.PACKAGE_PIN := io.sram_data(i)
    // }
    io.sram_data.write <> ram.io.sram.dataOut
    io.sram_data.read <> ram.io.sram.dataIn
    io.sram_data.writeEnable.setAllTo(ram.io.sram.oe_n)
    io.sram_addr <> ram.io.sram.addr
    io.sram_ce_n <> ram.io.sram.ce_n
    io.sram_oe_n <> ram.io.sram.oe_n
    io.sram_we_n <> ram.io.sram.we_n
    io.sram_lb_n <> ram.io.sram.sel_n(0)
    io.sram_ub_n <> ram.io.sram.sel_n(1)
    ram.io.clk90deg := clk_20mhz_90deg

    // val ram = new PipelinedMemoryBusRam(busConfig)
    // busSlaves += ram.io.bus -> SizeMapping(0x80000000l, 12 KiB)
  }

  for ((led, i) <- Seq(io.led1, io.led2, io.led3).zipWithIndex) {
    led := gpio.write(i) && gpio.writeEnable(i)
  }
  gpio.read := 0
}

object PQVexRiscvIcoboard {
  def main(args: Array[String]) : Unit = {
    SpinalConfig(
      mode = Verilog,
      targetDirectory = "rtl"
    ).generate(InOutWrapper(new PQVexRiscvIcoboard)).printPruned()
  }
}
