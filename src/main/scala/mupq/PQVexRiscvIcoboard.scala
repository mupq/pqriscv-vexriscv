package mupq

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.io.{TriStateArray, InOutWrapper}
import spinal.lib.bus.misc._
import spinal.lib.bus.simple._

import vexriscv._
import vexriscv.plugin._

case class IssiSRAMBus() extends Bundle with IMasterSlave {
  val addr = Bits(19 bits)
  val data = TriStateArray(16 bits)
  val nce = Bool /* ~ChipEnable */
  val noe = Bool /* ~OutputEnable */
  val nwe = Bool /* ~WriteEnable */
  val nlb = Bool /* ~LowerByte Control */
  val nub = Bool /* ~UpperByte Control */

  override def asMaster() : Unit = {
    out(addr,nce,noe,nwe,nlb,nub)
    master(data)
  }
}

class PipelinedMemoryBusSRAM(busConfig: PipelinedMemoryBusConfig) extends Component {
  require(busConfig.dataWidth == 32, "Only 32 bit busses supported")
  val io = new Bundle {
    val bus = slave(PipelinedMemoryBus(busConfig))
    val clk90deg = in Bool
    val sram = master(IssiSRAMBus())
  }

  val sramAddr = Reg(Bits(19 bit)) init(0)
  val sramDataOut = Reg(Bits(16 bit)) init(0)
  val sramDataIn = Bits(16 bit)
  val sramWrite = Reg(Bool) init(false)
  val sramMask = Reg(Bits(2 bit)) init(0)

  io.sram.addr := sramAddr
  sramDataIn := io.sram.data.read
  io.sram.data.write := sramDataOut
  io.sram.data.writeEnable setAllTo(sramWrite)
  io.sram.nce := False
  io.sram.noe := sramWrite
  io.sram.nwe := sramWrite ? !io.clk90deg | True
  io.sram.nlb := sramWrite ? !sramMask(0) | False
  io.sram.nub := sramWrite ? !sramMask(1) | False

  val fsm = new StateMachine() {
    val cmd = io.bus.cmd
    val rsp = io.bus.rsp
    cmd.ready := False
    rsp.valid := False

    val dataLow = Reg(Bits(16 bit)) init(0)
    rsp.data(15 downto 0) := dataLow
    rsp.data(31 downto 16) := sramDataIn

    val waitCmd : State = new State with EntryPoint {
      whenIsActive {
        sramWrite := False
        when(io.bus.cmd.valid) {
          sramAddr := cmd.address(19 downto 2).asBits ## False
          sramDataOut := cmd.data(15 downto 0)
          sramWrite := cmd.write
          sramMask := cmd.mask(1 downto 0)
          goto(lowerByte)
        }
      }
    }

    val lowerByte = new State {
      whenIsActive {
        dataLow := sramDataIn
        /* Setup read/write for upper byte */
        sramAddr := cmd.address(19 downto 2).asBits ## True
        sramDataOut := cmd.data(31 downto 16)
        sramWrite := cmd.write
        sramMask := cmd.mask(3 downto 2)
        cmd.ready := True
        when(sramWrite) {
          goto(waitCmd)
        }.otherwise {
          goto(upperByte)
        }
      }
    }

    val upperByte = new State {
      whenIsActive {
        rsp.valid := True
        goto(waitCmd)
      }
    }

  }
}

class PQVexRiscvIcoboard(
  val coreFrequency : HertzNumber = 20 MHz
) extends PQVexRiscv(
  cpuPlugins = PQVexRiscv.defaultPlugins,
  ibusRange = SizeMapping(0x80000000l, 128 KiB),
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
    val sram_addr = out Bits(19 bit)
    val sram_data = inout(Analog(Vec(Bool, 16)))
    val sram_nce = out Bool
    val sram_noe = out Bool
    val sram_nwe = out Bool
    val sram_nlb = out Bool
    val sram_nub = out Bool
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

    for (i <- 0 until 16) {
      val ice40io = new Ice40IO(0x29)
      ice40io.io.D_OUT_0 <> ram.io.sram.data.write(i)
      ice40io.io.OUTPUT_ENABLE <> ram.io.sram.data.writeEnable(i)
      ice40io.io.D_IN_0 <> ram.io.sram.data.read(i)
      io.sram_data(i) := ice40io.io.PACKAGE_PIN
      // ice40io.io.PACKAGE_PIN <> io.sram_data(i)
    }
    // io.sram_data <> ram.io.sram.data
    io.sram_addr <> ram.io.sram.addr
    io.sram_nce <> ram.io.sram.nce
    io.sram_noe <> ram.io.sram.noe
    io.sram_nwe <> ram.io.sram.nwe
    io.sram_nlb <> ram.io.sram.nlb
    io.sram_nub <> ram.io.sram.nub

    ram.io.clk90deg := clk_20mhz_90deg
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
    ).generate(new PQVexRiscvIcoboard).printPruned()
  }
}
