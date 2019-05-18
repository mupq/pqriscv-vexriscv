package mupq

import spinal.core._

/**
  * BlackBox model for the PLL present in Ice40 FPGAs.
  */
class Ice40PLLPad(divF: Int, divR: Int, divQ: Int) extends BlackBox {
  val io = new Bundle {
    val RESETB = in Bool
    val BYPASS = in Bool
    val PACKAGEPIN = in Bool
    val PLLOUTGLOBAL = out Bool
    val LOCK = out Bool
  }
  noIoPrefix()
  setBlackBoxName("SB_PLL40_PAD")
  addGeneric("FEEDBACK_PATH", "SIMPLE")
  addGeneric("DELAY_ADJUSTMENT_MODE_FEEDBACK", "FIXED")
  addGeneric("DELAY_ADJUSTMENT_MODE_RELATIVE", "FIXED")
  addGeneric("PLLOUT_SELECT", "GENCLK")
  addGeneric("FDA_FEEDBACK", B(0xF, 4 bits))
  addGeneric("FDA_RELATIVE", B(0xF, 4 bits))
  addGeneric("DIVF", B(divF, 7 bits))
  addGeneric("DIVR", B(divR, 4 bits))
  addGeneric("DIVQ", B(divQ, 3 bits))
  addGeneric("FILTER_RANGE", B(0x2, 3 bits))
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
