package mupq

import org.scalatest.FlatSpec

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.simple._
import spinal.sim._

import scala.collection.mutable.ArrayBuffer

import scala.util.Random

class SRAMTest extends FlatSpec {
  var sram: SimCompiled[IcoboardBusTest] = null
  "An SRAM controller" should "compile" in {
    sram = SimConfig.withFstWave.compile {
      val sram = new IcoboardBusTest(extClock = true, smallTest = false, counterBits = 8)
      sram.memory.fsm.stateReg.simPublic()
      sram
    }
  }

  it should "perform reads" in {
    sram.doSim("timing") { dut =>
      val period = (1e9 / (20 MHz).toLong).toLong

      // Asynchronous SRAM
      var sramSim = fork {
        var ram = ArrayBuffer.fill(1 << 19) {0}
        while(true) {
          sleep(period / 20)
          val rnd = Random.nextInt(1 << 16)
          val rnd2 = Random.nextInt(1 << 16)
          val mask = (if (dut.io.sram_lb_n.toBoolean) 0x0 else 0xFF) | (if (dut.io.sram_ub_n.toBoolean) 0x0 else 0xFF00)
          if (dut.io.sram_ce_n.toBoolean || dut.io.sram_oe_n.toBoolean || !dut.io.sram_we_n.toBoolean) {
            dut.io.sram_data.read #= rnd
          } else {
            dut.io.sram_data.read #= (ram(dut.io.sram_addr.toInt) & mask) | (rnd & (mask ^ 0xFFFF))
          }
          if (!dut.io.sram_ce_n.toBoolean && !dut.io.sram_we_n.toBoolean) {
            val oval = ram(dut.io.sram_addr.toInt) & (mask ^ 0xFFFF)
            val mask2 = dut.io.sram_data.writeEnable.toInt
            val nval = ((dut.io.sram_data.write.toInt & mask2) | rnd2 & (mask2 ^ 0xFFFF)) & mask
            ram.update(dut.io.sram_addr.toInt, nval | oval)
          }
        }
      }

      val clkDom = ClockDomain(dut.io.clk_100mhz, dut.io.reset)
      clkDom.forkStimulus(period)
      val clock90 = fork {
        dut.io.clk_90deg #= false
        while(true) {
          clkDom.waitSampling()
          sleep(period / 4)
          dut.io.clk_90deg #= true
          sleep(period / 2)
          dut.io.clk_90deg #= false
        }
      }

      clkDom.forkStimulus(period)
      // sleep(2000 * period)

      clkDom.waitSamplingWhere(dut.memory.fsm.stateReg.toEnum == dut.memory.fsm.enumOf(dut.memory.fsm.TEST))
      // sleep(10 * period)
    }
  }
}
