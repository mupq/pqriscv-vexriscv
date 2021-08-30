package mupq

import spinal.core._

/** BlackBox model for the PLL present in Ice40 FPGAs.
  */
class Ice40PLLPad(divF: Int, divR: Int, divQ: Int) extends BlackBox {
  val generic = new Generic {
    val FEEDBACK_PATH                  = "SIMPLE"
    val DELAY_ADJUSTMENT_MODE_FEEDBACK = "FIXED"
    val DELAY_ADJUSTMENT_MODE_RELATIVE = "FIXED"
    val PLLOUT_SELECT                  = "GENCLK"
    val FDA_FEEDBACK                   = B(0xf, 4 bits)
    val FDA_RELATIVE                   = B(0xf, 4 bits)
    val DIVF                           = B(divF, 7 bits)
    val DIVR                           = B(divR, 4 bits)
    val DIVQ                           = B(divQ, 3 bits)
    val FILTER_RANGE                   = B(0x2, 3 bits)
  }
  val io = new Bundle {
    val RESETB       = in Bool()
    val BYPASS       = in Bool()
    val PACKAGEPIN   = in Bool()
    val PLLOUTGLOBAL = out Bool()
    val LOCK         = out Bool()
  }
  noIoPrefix()
  setBlackBoxName("SB_PLL40_PAD")
}

class Ice40PLL2FCore(
  divF: Int,
  divR: Int,
  divQ: Int
)
extends BlackBox {
  val generic = new Generic {
    val FEEDBACK_PATH                  = "PHASE_AND_DELAY"
    val DELAY_ADJUSTMENT_MODE_FEEDBACK = "FIXED"
    val DELAY_ADJUSTMENT_MODE_RELATIVE = "FIXED"
    val PLLOUT_SELECT_PORTA            = "SHIFTREG_0deg"
    val PLLOUT_SELECT_PORTB            = "SHIFTREG_90deg"
    val SHIFTREG_DIV_MODE              = False
    val FDA_FEEDBACK                   = B(0xf, 4 bits)
    val FDA_RELATIVE                   = B(0xf, 4 bits)
    val DIVF                           = B(divF, 7 bits)
    val DIVR                           = B(divR, 4 bits)
    val DIVQ                           = B(divQ, 3 bits)
    val FILTER_RANGE                   = B(0x7, 3 bits)
  }
  val io = new Bundle {
    val RESETB        = in Bool()
    val BYPASS        = in Bool()
    val REFERENCECLK  = in Bool()
    val PLLOUTGLOBALA = out Bool()
    val PLLOUTGLOBALB = out Bool()
    val LOCK          = out Bool()
  }
  noIoPrefix()
  setBlackBoxName("SB_PLL40_2F_CORE")
}

class Ice40IO(pinType: Int = 0, pullup: Boolean = false, negTrigger: Boolean = false)
extends BlackBox {
  val generic = new Generic {
    val PIN_TYPE = B(pinType, 6 bits)
    val PULLUP   = if (pullup) True else False
    // val NEG_TRIGGER = if (negTrigger) True else False
    // val IO_STANDARD = "SB_LVCMOS"
  }
  val io = new Bundle {
    val PACKAGE_PIN = inout(Analog(Bool))
    // val LATCH_INPUT_ENABLE = in Bool
    // val CLOCK_ENABLE = in Bool
    // val INPUT_CLK = in Bool
    // val OUTPUT_CLK = in Bool
    val OUTPUT_ENABLE = in Bool()
    val D_OUT_0       = in Bool()
    // val D_OUT_1 = in Bool
    val D_IN_0 = out Bool()
    // val D_IN_1 = out Bool
  }
  noIoPrefix()
  setBlackBoxName("SB_IO")
}

/** BlackBox model for the large SPRAM blocks of Ice40 UltraPlus FPGAs.
  */
class Ice40SPRAM extends BlackBox {
  val io = new Bundle {
    val ADDRESS    = in Bits (14 bits)
    val DATAIN     = in Bits (16 bits)
    val DATAOUT    = out Bits (16 bits)
    val MASKWREN   = in Bits (4 bits)
    val WREN       = in Bool()
    val CHIPSELECT = in Bool()
    val CLOCK      = in Bool()
    val STANDBY    = in Bool()
    val SLEEP      = in Bool()
    val POWEROFF   = in Bool()
  }
  noIoPrefix()
  mapClockDomain(clock = io.CLOCK)
  setBlackBoxName("SB_SPRAM256KA")
}

/** BlackBox Model for DSA
  */
class Ice40Multiplier(registerOutput: Boolean = false) extends BlackBox {
  val generics = new Generic {
    val A_REG                    = B"0"
    val B_REG                    = B"0"
    val C_REG                    = B"0"
    val D_REG                    = B"0"
    val TOP_8X8_MULT_REG         = B"0"
    val BOT_8X8_MULT_REG         = B"0"
    val PIPELINE_16X16_MULT_REG1 = B"0"
    val PIPELINE_16X16_MULT_REG2 = if (registerOutput) B"1" else B"0"
    val TOPOUTPUT_SELECT         = B"11"
    val TOPADDSUB_LOWERINPUT     = B"00"
    val TOPADDSUB_UPPERINPUT     = B"0"
    val TOPADDSUB_CARRYSELECT    = B"00"
    val BOTOUTPUT_SELECT         = B"11"
    val BOTADDSUB_LOWERINPUT     = B"00"
    val BOTADDSUB_UPPERINPUT     = B"0"
    val BOTADDSUB_CARRYSELECT    = B"00"
    val MODE_8X8                 = B"0"
    val A_SIGNED                 = B"0"
    val B_SIGNED                 = B"0"
  }

  val io = new Bundle {
    val CLK        = in Bool()
    val CE         = in Bool()
    val A          = in Bits (16 bits)
    val AHOLD      = in Bool()
    val B          = in Bits (16 bits)
    val BHOLD      = in Bool()
    val C          = in Bits (16 bits)
    val CHOLD      = in Bool()
    val D          = in Bits (16 bits)
    val DHOLD      = in Bool()
    val IRSTTOP    = in Bool()
    val ORSTTOP    = in Bool()
    val OLOADTOP   = in Bool()
    val ADDSUBTOP  = in Bool()
    val OHOLDTOP   = in Bool()
    val O          = out Bits (32 bits)
    val IRSTBOT    = in Bool()
    val ORSTBOT    = in Bool()
    val OLOADBOT   = in Bool()
    val ADDSUBBOT  = in Bool()
    val OHOLDBOT   = in Bool()
    val CI         = in Bool()
    val CO         = out Bool()
    val ACCUMCI    = in Bool()
    val ACCUMCO    = out Bool()
    val SIGNEXTIN  = in Bool()
    val SIGNEXTOUT = out Bool()
  }
  noIoPrefix()
  mapCurrentClockDomain(io.CLK, enable = io.CE)
  setBlackBoxName("SB_MAC16")

  def assignDefaults = {
    // io.A := B(0, 16 bits).allowOverride
    // io.B := B(0, 16 bits).allowOverride
    io.C := B(0, 16 bits)
    io.D := B(0, 16 bits)
    io.AHOLD := False
    io.BHOLD := False
    io.CHOLD := False
    io.DHOLD := False
    io.ADDSUBTOP := False
    io.IRSTTOP := False
    io.ORSTTOP := False
    io.OHOLDTOP := False
    io.OLOADTOP := False
    io.ADDSUBBOT := False
    io.IRSTBOT := False
    io.ORSTBOT := False
    io.OHOLDBOT := False
    io.OLOADBOT := False
    io.CI := False
    io.ACCUMCI := False
    io.SIGNEXTIN := False
  }
}
