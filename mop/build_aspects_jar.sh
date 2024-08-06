#!/bin/bash

SCRIPT_DIR=$( cd $( dirname $0 ) && pwd )
PROPS_DIR=""
TRACEMOP_DIR=""

function build_aspects_jar() {
  local orig_classpath=${CLASSPATH}
  local orig_path=${PATH}

  export PATH=${TRACEMOP_DIR}/rv-monitor/target/release/rv-monitor/bin:${TRACEMOP_DIR}/javamop/target/release/javamop/javamop/bin:${TRACEMOP_DIR}/rv-monitor/target/release/rv-monitor/lib/rv-monitor-rt.jar:${TRACEMOP_DIR}/rv-monitor/target/release/rv-monitor/lib/rv-monitor.jar:${PATH}
  export CLASSPATH=${TRACEMOP_DIR}/rv-monitor/target/release/rv-monitor/lib/rv-monitor-rt.jar:${TRACEMOP_DIR}/rv-monitor/target/release/rv-monitor/lib/rv-monitor.jar:${CLASSPATH}
  
  local tmp_props=/tmp/rv-props
  rm -rf /tmp/rv-props
  cp -r ${PROPS_DIR} ${tmp_props}
  
  # Generate .aj files and MultiSpec_1RuntimeMonitor.java
  cp ${PROPS_DIR}/../BaseAspect_new.aj ${tmp_props}/BaseAspect.aj
  
  for spec in ${tmp_props}/*.mop; do
    javamop -baseaspect ${tmp_props}/BaseAspect.aj -emop ${spec} -internalBehaviorObserving # Generate .aj
  done
  
  rm -rf ${tmp_props}/classes/mop; mkdir -p ${tmp_props}/classes/mop
  rv-monitor -merge -d ${tmp_props}/classes/mop/ ${tmp_props}/*.rvm -locationFromAjc # Generate MultiSpec_1RuntimeMonitor.java
  
  cp ${tmp_props}/classes/mop/MultiSpec_1RuntimeMonitor.java ${tmp_props}/MultiSpec_1RuntimeMonitor.java
  rm -rf ${tmp_props}/classes/ ${tmp_props}/*.mop ${tmp_props}/*.rvm  # Only keep .aj and MultiSpec_1RuntimeMonitor.java
  
  pushd ${SCRIPT_DIR}
  ajc -Xlint:ignore -1.8 -encoding UTF-8 -showWeaveInfo -verbose -outjar myaspects.jar ${tmp_props}/*
  popd
  
  rm -rf ${tmp_props}
  
  export CLASSPATH=${orig_classpath}
  export PATH=${orig_path}
}

build_aspects_jar
