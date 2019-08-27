package mupq

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.io.{TriStateArray, InOutWrapper}
import spinal.lib.bus.misc._
import spinal.lib.bus.simple._

class SRAMController(val addrWidth : Int = 16, dataWidth : Int = 16) extends Component {
  require(dataWidth % 8 == 0, "dataWidth must be multiple of 8")
  val busConfig = PipelinedMemoryBusConfig(addrWidth, dataWidth)

  val io = new Bundle {
    val bus = slave(PipelinedMemoryBus(busConfig))
    val sram = master(GenericSRAM(addrWidth, dataWidth))
    val clk90deg = in Bool
  }

  val addr = Reg(Bits(addrWidth bits))
  val dataOut = Reg(Bits(dataWidth bits))
  /* Chip and output enable always on, only write enable is toggled */
  val ce_n = RegNext(False) init(true)
  val we_n = RegNext(True) init(true)
  val selAllSet = B((dataWidth / 8) bits, default -> true)
  val sel_n = RegNext(selAllSet) init(selAllSet)

  io.bus.cmd.ready := False

  val dataIn = Reg(Bits(dataWidth bits))
  val valid = RegNext(False) init(false)
  io.bus.rsp.data := dataIn
  io.bus.rsp.valid := valid

  io.sram.addr := addr
  io.sram.dataOut := dataOut
  io.sram.ce_n := ce_n
  io.sram.we_n := we_n || !io.clk90deg
  io.sram.oe_n := !we_n
  io.sram.sel_n := sel_n

  val fsm = new StateMachine() {

    addr.assignDontCare()
    dataOut.assignDontCare()

    val IDLE = new State with EntryPoint
    val BYTE = new State
    IDLE.whenIsActive {
      when(io.bus.cmd.valid) {
        io.bus.cmd.ready := True
        addr := io.bus.cmd.address.asBits
        dataOut := io.bus.cmd.data
        we_n := !io.bus.cmd.write
        sel_n := ~io.bus.cmd.mask
        goto(BYTE)
      }
    }
    BYTE.whenIsActive {
      dataIn := io.sram.dataIn
      valid := we_n
      goto(IDLE)
    }
  }
}


class IcoboardBusTest(var extClock : Boolean = false, counterBits : Int = 19, smallTest : Boolean = true) extends Component {
  val io = new Bundle {
    val clk_100mhz = in Bool
    val clk_90deg = if (extClock) in Bool else null
    val reset = if (extClock) in Bool else null
    // LED's
    val led1 = out Bool
    val led2 = out Bool
    val led3 = out Bool
    // // PMOD
    // val pmod = out Bool
    // val led2 = out Bool
    // val led3 = out Bool
    /* SRAM */
    val sram_addr = out Bits(19 bits)
    val sram_data = master( TriStateArray(16 bits) )
    val sram_ce_n = out Bool
    val sram_oe_n = out Bool
    val sram_we_n = out Bool
    val sram_lb_n = out Bool
    val sram_ub_n = out Bool
  }

  noIoPrefix()
  val clk_20mhz = Bool
  val clk_20mhz_90deg = Bool
  val reset = Bool

  if (extClock) {
    clk_20mhz := io.clk_100mhz
    clk_20mhz_90deg := io.clk_90deg
    reset := io.reset
  } else {
    /* PLL config 100mhz -> 20mhz (using icepll tool of icestorm) */
    val pll = new Ice40PLL2FCore(
      divR = 4,
      divF = 0,
      divQ = 5)

    pll.io.REFERENCECLK := io.clk_100mhz
    pll.io.BYPASS := False
    pll.io.RESETB := True

    clk_20mhz := pll.io.PLLOUTGLOBALA
    clk_20mhz_90deg := pll.io.PLLOUTCOREB
    reset := !pll.io.LOCK
  }

  val pllDomain = ClockDomain(
    clock = clk_20mhz, reset = reset,
    frequency = FixedFrequency(20 MHz)
  )

  val memory = new ClockingArea(pllDomain) {
    val busConfig = if (smallTest)
                      PipelinedMemoryBusConfig(19, 16)
                    else
                      PipelinedMemoryBusConfig(18, 32)
    val bus = PipelinedMemoryBus(busConfig)
    if (smallTest) {
      val ram = new SRAMController(19, 16)
      ram.io.clk90deg := clk_20mhz_90deg
      io.sram_data.write <> ram.io.sram.dataOut
      io.sram_data.read <> ram.io.sram.dataIn
      io.sram_data.writeEnable.setAllTo(ram.io.sram.oe_n)
      io.sram_addr <> ram.io.sram.addr
      io.sram_ce_n <> ram.io.sram.ce_n
      io.sram_oe_n <> ram.io.sram.oe_n
      io.sram_we_n <> ram.io.sram.we_n
      io.sram_lb_n <> ram.io.sram.sel_n(0)
      io.sram_ub_n <> ram.io.sram.sel_n(1)
      bus <> ram.io.bus
    } else {
      val ram = new PipelinedMemoryBusSRAM(PipelinedMemoryBusConfig(20, 32))
      ram.io.clk90deg := clk_20mhz_90deg
      io.sram_data.write <> ram.io.sram.dataOut
      io.sram_data.read <> ram.io.sram.dataIn
      io.sram_data.writeEnable.setAllTo(ram.io.sram.oe_n)
      io.sram_addr <> ram.io.sram.addr
      io.sram_ce_n <> ram.io.sram.ce_n
      io.sram_oe_n <> ram.io.sram.oe_n
      io.sram_we_n <> ram.io.sram.we_n
      io.sram_lb_n <> ram.io.sram.sel_n(0)
      io.sram_ub_n <> ram.io.sram.sel_n(1)
      ram.io.bus.rsp <> bus.rsp
      ram.io.bus.cmd.ready <> bus.cmd.ready
      ram.io.bus.cmd.valid <> bus.cmd.valid
      ram.io.bus.cmd.data <> bus.cmd.data
      ram.io.bus.cmd.write <> bus.cmd.write
      ram.io.bus.cmd.mask <> bus.cmd.mask
      ram.io.bus.cmd.address := bus.cmd.address << 2
    }

    val fsm = new StateMachine() {
      val blinky = Counter(21 bit)
      val blinkyReg = Reg(Bool) init(false)
      when(blinky.willOverflow) {
        blinkyReg := !blinkyReg
      }

      val counter = Counter(counterBits bit)
      val lsfr = Reg(Bits(busConfig.dataWidth bit))
      bus.cmd.address := counter.resized
      bus.cmd.data := lsfr
      // bus.cmd.mask := B"11"
      bus.cmd.mask.setAll()
      bus.cmd.write := False
      bus.cmd.valid := False
      when(counter.willClear || counter.willOverflow) {
        lsfr := 0xACE1
      }.elsewhen(counter.willIncrement) {
        val bit = lsfr(0) ^ lsfr(2) ^ lsfr(3) ^ lsfr(5)
        lsfr := bit ## lsfr(lsfr.high downto 1)
      }

      val error = Reg(Bool) init(false)
      io.led1 := False
      io.led2 := False
      io.led3 := False

      val INIT = new State with EntryPoint
      val SIMPLEWRITE = new State
      val SIMPLEREAD = new State
      val WRITE = new State
      val READ = new State
      val TEST = new State
      INIT.whenIsActive {
        error := False
        counter.clear
        goto(SIMPLEWRITE)
      }

      SIMPLEWRITE.whenIsActive {
        bus.cmd.data := 0xDEAD
        bus.cmd.valid := True
        bus.cmd.write := True
        when(bus.cmd.fire) {
          goto(SIMPLEREAD)
        }
      }

      SIMPLEREAD.whenIsActive {
        bus.cmd.data := 0x0000
        bus.cmd.valid := counter === U(0)
        bus.cmd.write := False
        when(bus.cmd.fire) {
          counter.increment
        }
        when(bus.rsp.fire) {
          goto(WRITE)
          when(bus.rsp.data =/= 0xDEAD) {
            goto(TEST)
          }
        }
      }

      SIMPLEREAD.onExit {
        counter.clear
      }


      WRITE.whenIsActive {
        bus.cmd.valid := True
        bus.cmd.write := True
        when(bus.cmd.fire) {
          counter.increment
        }
        blinky.increment
        when(counter.willOverflowIfInc) {
          goto(READ)
        }
        io.led1 := blinkyReg
      }

      val valid = Reg(Bool) init(false)

      READ.onEntry {
        counter.clear()
        error := False
        valid := True
      }
      READ.whenIsActive {
        bus.cmd.data := 0x0000
        bus.cmd.valid := valid
        when(bus.cmd.fire) {
          valid := False
        }
        when(bus.rsp.fire) {
          error := error || (bus.rsp.data =/= lsfr)
          valid := True
          counter.increment
        }
        blinky.increment
        when(counter.willOverflowIfInc) {
          goto(TEST)
        }
        when(error) {
          goto(TEST)
        }
        io.led2 := blinkyReg
      }

      TEST.whenIsActive {
        blinky.increment
        when(error) {
          io.led1 := blinkyReg
          io.led2 := !blinkyReg
          io.led3 := blinkyReg
        }.otherwise {
          io.led1 := blinkyReg
          io.led2 := blinkyReg
          io.led3 := True
        }
      }
    }
  }
}

object IcoboardBusTest {
  def main(args: Array[String]) : Unit = {
    SpinalConfig(
      mode = Verilog,
      targetDirectory = "rtl"
    ).generate(InOutWrapper(new IcoboardBusTest)).printPruned()
  }
}
