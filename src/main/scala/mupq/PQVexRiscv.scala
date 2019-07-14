package mupq

import scala.collection.mutable.ArrayBuffer

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.misc._
import spinal.lib.bus.simple._
import spinal.lib.io._
import spinal.lib.com.jtag._
import spinal.lib.com.uart._

import vexriscv._
import vexriscv.demo.MuraxApb3Timer
import vexriscv.plugin._

abstract class PQVexRiscv(
  cpuPlugins : Seq[Plugin[VexRiscv]],
  ibusRange : SizeMapping,
  genUART : Boolean = true,
  gpioWidth : Int = 0,
  genTimer : Boolean = false
) extends Component {
  val coreFrequency : HertzNumber

  /* Clock and resets */

  val asyncReset: Bool = Bool

  val mainClock: Bool = Bool

  val resetCtrlClockDomain: ClockDomain = ClockDomain(
    clock = mainClock,
    config = ClockDomainConfig(resetKind = BOOT))

  val resetCtrl = new ClockingArea(resetCtrlClockDomain) {
    val bufferedReset = BufferCC(asyncReset)

    val mainClockReset = RegNext(bufferedReset)
    val systemClockReset = RegNext(bufferedReset)
  }

  val systemClockDomain: ClockDomain = ClockDomain(
    clock = mainClock,
    reset = resetCtrl.systemClockReset,
    frequency = FixedFrequency(coreFrequency))

  val debugClockDomain: ClockDomain = ClockDomain(
    clock = mainClock,
    reset = resetCtrl.mainClockReset,
    frequency = FixedFrequency(coreFrequency))

  /* Bus interconnect */
  val busConfig = PipelinedMemoryBusConfig(
    addressWidth = 32,
    dataWidth = 32
  )

  val busSlaves = ArrayBuffer[(PipelinedMemoryBus, SizeMapping)]()
  val busMasters = ArrayBuffer[(PipelinedMemoryBus, SizeMapping)]()

  /* VexRiscv Core */
  var jtag : Jtag = null

  val core = new ClockingArea(systemClockDomain) {
    val timerInterrupt = False
    val externalInterrupt = False

    val config = VexRiscvConfig(
      plugins = cpuPlugins ++ Seq(new DebugPlugin(debugClockDomain, 3)))

    val cpu = new VexRiscv(config)
    /* Wire the Busses / Lines to the plugins */
    var ibus : PipelinedMemoryBus = PipelinedMemoryBus(busConfig)
    var dbus : PipelinedMemoryBus = PipelinedMemoryBus(busConfig)
    for (plugin <- cpu.plugins) plugin match {
      case plugin: IBusSimplePlugin =>
        ibus << plugin.iBus.toPipelinedMemoryBus()
      case plugin: DBusSimplePlugin =>
        dbus << plugin.dBus.toPipelinedMemoryBus()
        plugin.dBus.rsp.error := False
      case plugin: CsrPlugin =>
        plugin.externalInterrupt := externalInterrupt
        plugin.timerInterrupt := timerInterrupt
      case plugin: DebugPlugin =>
        plugin.debugClockDomain {
          resetCtrl.systemClockReset setWhen (RegNext(plugin.io.resetOut))
          jtag = plugin.io.bus.fromJtag()
        }
      case _ =>
    }

    busMasters += dbus -> SizeMapping(0l, (1l << 32l))
    busMasters += ibus -> ibusRange
  }

  /* Peripherals */

  var gpio : TriStateArray = null

  var uart : Uart = null

  val peripherals = new ClockingArea(systemClockDomain) {
    if (gpioWidth > 0) {
      gpio = TriStateArray(gpioWidth bits)
    }

    if (genUART) {
      uart = Uart()
    }

    if(genUART || gpioWidth > 0 || genTimer) {
      val apbBridge = new PipelinedMemoryBusToApbBridge(
        apb3Config = Apb3Config(
          addressWidth = 20,
          dataWidth = 32
        ),
        pipelineBridge = false,
        pipelinedMemoryBusConfig = busConfig
      )

      busSlaves += apbBridge.io.pipelinedMemoryBus -> SizeMapping(0xF0000000l, 1 MiB)

      val apbMapping = ArrayBuffer[(Apb3, SizeMapping)]()

      if (gpioWidth > 0) {
        val gpioACtrl = Apb3Gpio(gpioWidth = gpioWidth, withReadSync = true)
        gpio <> gpioACtrl.io.gpio
        apbMapping += gpioACtrl.io.apb -> (0x00000, 4 KiB)
      }

      if (genUART) {
        val uartCtrlConfig = UartCtrlMemoryMappedConfig(
          uartCtrlConfig = UartCtrlGenerics(
            dataWidthMax      = 8,
            clockDividerWidth = 20,
            preSamplingSize   = 1,
            samplingSize      = 3,
            postSamplingSize  = 1
          ),
          initConfig = UartCtrlInitConfig(
            baudrate = 115200,
            dataLength = 7,  //7 => 8 bits
            parity = UartParityType.NONE,
            stop = UartStopType.ONE
          ),
          busCanWriteClockDividerConfig = false,
          busCanWriteFrameConfig = false,
          txFifoDepth = 16,
          rxFifoDepth = 16
        )

        val uartCtrl = Apb3UartCtrl(uartCtrlConfig)
        uart <> uartCtrl.io.uart
        core.externalInterrupt setWhen(uartCtrl.io.interrupt)
        apbMapping += uartCtrl.io.apb  -> (0x10000, 4 KiB)
      }

      if (genTimer) {
        val timer = new MuraxApb3Timer()
        core.timerInterrupt setWhen(timer.io.interrupt)
        apbMapping += timer.io.apb -> (0x20000, 4 KiB)
      }

      val apbDecoder = Apb3Decoder(
        master = apbBridge.io.apb,
        slaves = apbMapping
      )
    }
  }

  def buildInterconnect() : Unit = {
    assert(!SizeMapping.verifyOverlapping(busSlaves.map(_._2)))
    val crossbar = new ClockingArea(systemClockDomain) {
      val interconnect = new PipelinedMemoryBusInterconnect()
      interconnect.areaConfig()
      /* Setup the interconnect */
      interconnect.addSlaves(busSlaves: _*)
      /* Check which masters overlap with which slaves */
      def overlaps(a : SizeMapping, b : SizeMapping) : Boolean = if (a.base < b.base) a.end >= b.base else b.end >= a.base
      interconnect.addMasters(busMasters.map(m => m._1 -> busSlaves.filter(s => overlaps(m._2, s._2)).map(s => s._1).toSeq): _*)
    }
  }

  Component.current.addPrePopTask(() => buildInterconnect())
}

object PQVexRiscv
{
  def defaultPlugins: Seq[Plugin[VexRiscv]] = Seq(
      new IBusSimplePlugin(
        resetVector = 0x80000000l,
        cmdForkOnSecondStage = true,
        cmdForkPersistence = false,
        prediction = NONE,
        catchAccessFault = false,
        compressedGen = false
      ),
      new DBusSimplePlugin(
        catchAddressMisaligned = false,
        catchAccessFault = false,
        earlyInjection = false
      ),
      new CsrPlugin(CsrPluginConfig.smallest(0x80000000l).copy(mtvecAccess = CsrAccess.READ_WRITE, mcycleAccess = CsrAccess.READ_ONLY, minstretAccess = CsrAccess.READ_ONLY)),
      new DecoderSimplePlugin(
        catchIllegalInstruction = false
      ),
      new RegFilePlugin(
        regFileReadyKind = plugin.SYNC,
        zeroBoot = false
      ),
      new IntAluPlugin,
      new SrcPlugin(
        separatedAddSub = false,
        executeInsertion = false
      ),
      new FullBarrelShifterPlugin,
      new Mul16Plugin,
      new MulDivIterativePlugin(false, true, 32, 1),
      new HazardSimplePlugin(
        bypassExecute = true,
        bypassMemory = true,
        bypassWriteBack = true,
        bypassWriteBackBuffer = true,
        pessimisticUseSrc = false,
        pessimisticWriteRegFile = false,
        pessimisticAddressMatch = false
      ),
      new BranchPlugin(
        earlyBranch = false,
        catchAddressMisaligned = false
      ),
      new YamlPlugin("cpu0.yaml")
    )
}
