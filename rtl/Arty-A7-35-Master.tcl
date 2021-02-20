## This file is a general .xdc for the Arty A7-35 Rev. D
## To use it in a project:
## - uncomment the lines corresponding to used pins
## - rename the used ports (in each line, after get_ports) according to the top level signal names in the project

## Clock signal
set_property -dict { PACKAGE_PIN E3    IOSTANDARD LVCMOS33 } [get_ports { CLK }]; #IO_L12P_T1_MRCC_35 Sch=gclk[100]
set group_args {}
for {set clockfreq 100} {$clockfreq<=200} {incr clockfreq 10} {
    set clockperiod [expr  {1.0e9 / (${clockfreq} * 1.0e6)}]
    puts "Adding clock sys_clk_pin_${clockfreq} with period ${clockperiod}"
    create_clock -add -name "sys_clk_pin_${clockfreq}" -period ${clockperiod} [get_ports { CLK }];
    lappend group_args -group "sys_clk_pin_${clockfreq}"
}

set_clock_groups -name sys_clk_pin -physically_exclusive {*}${group_args}

## Pmod Header JD
set_property -dict { PACKAGE_PIN D4    IOSTANDARD LVCMOS33 } [get_ports { TDO }]; #IO_L11N_T1_SRCC_35 Sch=jd[1]
set_property -dict { PACKAGE_PIN F4    IOSTANDARD LVCMOS33 } [get_ports { TCK }]; #IO_L13P_T2_MRCC_35 Sch=jd[3]
set_property -dict { PACKAGE_PIN E2    IOSTANDARD LVCMOS33 } [get_ports { TDI }]; #IO_L14P_T2_SRCC_35 Sch=jd[7]
set_property -dict { PACKAGE_PIN D2    IOSTANDARD LVCMOS33 } [get_ports { TMS }]; #IO_L14N_T2_SRCC_35 Sch=jd[8]

## USB-UART Interface
set_property -dict { PACKAGE_PIN D10   IOSTANDARD LVCMOS33 } [get_ports { TXD }]; #IO_L19N_T3_VREF_16 Sch=uart_rxd_out
set_property -dict { PACKAGE_PIN A9    IOSTANDARD LVCMOS33 } [get_ports { RXD }]; #IO_L14N_T2_SRCC_16 Sch=uart_txd_in

set_property -dict { PACKAGE_PIN C2    IOSTANDARD LVCMOS33 } [get_ports { RST }];
