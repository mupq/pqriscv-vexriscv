set BUILD_DATE [ clock format [ clock seconds ] -format %m%d%Y ]
set BUILD_TIME [ clock format [ clock seconds ] -format %H%M%S ]

set PROJECT_NAME "PQVexRiscvArty"

# Use output directory to keep it clean
set OUTPUT_DIR "./$PROJECT_NAME"
set PART_NUMBER "xc7a35ticsg324-1L"

# synthesis related settings
set SYNTH_ARGS ""
append SYNTH_ARGS " " -flatten_hierarchy " " rebuilt " "
append SYNTH_ARGS " " -gated_clock_conversion " " off " "
append SYNTH_ARGS " " -bufg " {" 12 "} "
append SYNTH_ARGS " " -fanout_limit " {" 10000 "} "
append SYNTH_ARGS " " -directive " " Default " "
append SYNTH_ARGS " " -fsm_extraction " " auto " "
append SYNTH_ARGS " " -resource_sharing " " auto " "
append SYNTH_ARGS " " -control_set_opt_threshold " " auto " "
append SYNTH_ARGS " " -shreg_min_size " {" 5 "} "
# Don't constrain the number of BRAMs and DSPs
append SYNTH_ARGS " " -max_bram " {" -1 "} "
append SYNTH_ARGS " " -max_dsp " {" -1 "} "
append SYNTH_ARGS " " -cascade_dsp " " auto " "
append SYNTH_ARGS " " -verbose

set DEFINES ""
append DEFINES -verilog_define " " USE_DEBUG " "

set TOP_MODULE "PQVexRiscvArty"

# Create project
create_project -force $PROJECT_NAME $OUTPUT_DIR 

# Design files and constraints
read_verilog "PQVexRiscvArty.v"
read_xdc Arty-A7-35-Master.xdc

# Synthesize (eval because of the SYNTH_ARGS)
eval "synth_design $DEFINES $SYNTH_ARGS -top $TOP_MODULE -part $PART_NUMBER"
report_timing_summary -file $OUTPUT_DIR/${PROJECT_NAME}_post_synth_tim.rpt
report_utilization -file $OUTPUT_DIR/${PROJECT_NAME}_post_synth_util.rpt
write_checkpoint -force $OUTPUT_DIR/${PROJECT_NAME}_post_synth.dcp

# Optimize Design
opt_design -directive Explore
report_timing_summary -file $OUTPUT_DIR/${PROJECT_NAME}_post_opt_tim.rpt
report_utilization -file $OUTPUT_DIR/${PROJECT_NAME}_post_opt_util.rpt
write_checkpoint -force $OUTPUT_DIR/${PROJECT_NAME}_post_opt.dcp
set_property SEVERITY {ERROR} [get_drc_checks DSPS-*]
# Run DRC on opt design to catch early issues like comb loops
report_drc -file $OUTPUT_DIR/${PROJECT_NAME}_post_opt_drc.rpt

# Place Design
place_design -directive Explore 
report_timing_summary -file $OUTPUT_DIR/${PROJECT_NAME}_post_place_tim.rpt
report_utilization -file $OUTPUT_DIR/${PROJECT_NAME}_post_place_util.rpt
write_checkpoint -force $OUTPUT_DIR/${PROJECT_NAME}_post_place.dcp

# Post Place Phys Opt
phys_opt_design -directive AggressiveExplore
report_timing_summary -file $OUTPUT_DIR/${PROJECT_NAME}_post_place_physopt_tim.rpt
report_utilization -file $OUTPUT_DIR/${PROJECT_NAME}_post_place_physopt_util.rpt
write_checkpoint -force $OUTPUT_DIR/${PROJECT_NAME}_post_place_physopt.dcp

# Route Design
route_design -directive Explore
report_timing_summary -file $OUTPUT_DIR/${PROJECT_NAME}_post_route_tim.rpt
report_timing_summary -file ${PROJECT_NAME}.time
report_utilization -hierarchical -file $OUTPUT_DIR/${PROJECT_NAME}_post_route_util.rpt
report_route_status -file $OUTPUT_DIR/${PROJECT_NAME}_post_route_status.rpt
report_io -file $OUTPUT_DIR/${PROJECT_NAME}_post_route_io.rpt
report_power -file $OUTPUT_DIR/${PROJECT_NAME}_post_route_power.rpt
report_design_analysis -logic_level_distribution \
 -of_timing_paths [get_timing_paths -max_paths 10000 \
 -slack_lesser_than 0] \
 -file $OUTPUT_DIR/${PROJECT_NAME}_post_route_vios.rpt
write_checkpoint -force $OUTPUT_DIR/${PROJECT_NAME}_post_route.dcp

# Get the Slack
set WNS [get_property SLACK [get_timing_paths -max_paths 1 -nworst 1 -setup]]
puts "Post Route WNS = $WNS"

# Write out bitfile
write_debug_probes -force $OUTPUT_DIR/${PROJECT_NAME}_${BUILD_DATE}_${BUILD_TIME}_${WNS}ns.ltx
write_bitstream -force ${PROJECT_NAME}.bit \
 -bin_file
