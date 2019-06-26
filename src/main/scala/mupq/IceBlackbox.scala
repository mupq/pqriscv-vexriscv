package mupq

import spinal.core._

/**
  * BlackBox model for the PLL present in Ice40 FPGAs.
  */
class Ice40PLLPad(divF: Int, divR: Int, divQ: Int) extends BlackBox {
  val generic = new Generic {
    val FEEDBACK_PATH = "SIMPLE"
    val DELAY_ADJUSTMENT_MODE_FEEDBACK = "FIXED"
    val DELAY_ADJUSTMENT_MODE_RELATIVE = "FIXED"
    val PLLOUT_SELECT = "GENCLK"
    val FDA_FEEDBACK = B(0xF, 4 bits)
    val FDA_RELATIVE = B(0xF, 4 bits)
    val DIVF = B(divF, 7 bits)
    val DIVR = B(divR, 4 bits)
    val DIVQ = B(divQ, 3 bits)
    val FILTER_RANGE = B(0x2, 3 bits)
  }
  val io = new Bundle {
    val RESETB = in Bool
    val BYPASS = in Bool
    val PACKAGEPIN = in Bool
    val PLLOUTGLOBAL = out Bool
    val LOCK = out Bool
  }
  noIoPrefix()
  setBlackBoxName("SB_PLL40_PAD")
}

class Ice40PLL2FCore(
  divF: Int, divR: Int, divQ: Int
) extends BlackBox {
  val generic = new Generic {
    val FEEDBACK_PATH = "PHASE_AND_DELAY"
    val DELAY_ADJUSTMENT_MODE_FEEDBACK = "FIXED"
    val DELAY_ADJUSTMENT_MODE_RELATIVE = "FIXED"
    val PLLOUT_SELECT_PORTA = "SHIFTREG_0deg"
    val PLLOUT_SELECT_PORTB = "SHIFTREG_90deg"
    val SHIFTREG_DIV_MODE = False
    val FDA_FEEDBACK = B(0xF, 4 bits)
    val FDA_RELATIVE = B(0xF, 4 bits)
    val DIVF = B(divF, 7 bits)
    val DIVR = B(divR, 4 bits)
    val DIVQ = B(divQ, 3 bits)
    val FILTER_RANGE = B(0x7, 3 bits)
  }
  val io = new Bundle {
    val RESETB = in Bool
    val BYPASS = in Bool
    val REFERENCECLK = in Bool
    val PLLOUTGLOBALA = out Bool
    val PLLOUTGLOBALB = out Bool
    val LOCK = out Bool
  }
  noIoPrefix()
  setBlackBoxName("SB_PLL40_2F_CORE")
}

class Ice40IO(pinType: Int = 0, pullup: Boolean = false, negTrigger: Boolean = false) extends BlackBox {
  val generic = new Generic {
    val PIN_TYPE = B(pinType, 6 bits)
    val PULLUP = if (pullup) True else False
    // val NEG_TRIGGER = if (negTrigger) True else False
    // val IO_STANDARD = "SB_LVCMOS"
  }
  val io = new Bundle {
    val PACKAGE_PIN = inout(Analog(Bool))
    // val LATCH_INPUT_ENABLE = in Bool
    // val CLOCK_ENABLE = in Bool
    // val INPUT_CLK = in Bool
    // val OUTPUT_CLK = in Bool
    val OUTPUT_ENABLE = in Bool
    val D_OUT_0 = in Bool
    // val D_OUT_1 = in Bool
    val D_IN_0 = out Bool
    // val D_IN_1 = out Bool
  }
  noIoPrefix()
  setBlackBoxName("SB_IO")
}

/**
  * BlackBox model for the large SPRAM blocks of Ice40 UltraPlus FPGAs.
  */
class Ice40SPRAM extends BlackBox {
  val io = new Bundle {
    val ADDRESS = in Bits (14 bits)
    val DATAIN = in Bits (16 bits)
    val DATAOUT = out Bits (16 bits)
    val MASKWREN = in Bits (4 bits)
    val WREN = in Bool
    val CHIPSELECT = in Bool
    val CLOCK = in Bool
    val STANDBY = in Bool
    val SLEEP = in Bool
    val POWEROFF = in Bool
  }
  noIoPrefix()
  mapClockDomain(clock = io.CLOCK)
  setBlackBoxName("SB_SPRAM256KA")
}
