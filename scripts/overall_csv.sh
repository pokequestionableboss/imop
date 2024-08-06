#!/bin/bash
#
# Usage: overall_csv.sh <projects-list> <iemop-timed-dir> <iemop-stats-dir> <paper-evo-dir> <ltw-stats-dir> <output-dir>
#
PROJECTS_LIST=$1
IEMOP_TIMED_DIR=$2
IEMOP_STATS_DIR=$3
PAPER_EVOLUTION_DIR=$4
LTW_STATS_DIR=$5
OUTPUT_DIR=$6

if [[ ! -f ${PROJECTS_LIST} || ! -d ${IEMOP_TIMED_DIR} || ! -d ${IEMOP_STATS_DIR} || ! -d ${PAPER_EVOLUTION_DIR} || ! -d ${LTW_STATS_DIR} || -z ${OUTPUT_DIR} ]]; then
  echo "Usage: overall_csv.sh <projects-list> <iemop-timed-dir> <iemop-stats-dir> <paper-evo-dir> <ltw-stats-dir> <output-dir>"
  exit 1
fi

mkdir -p ${OUTPUT_DIR}

function generate_verification_csv() {
  local project=$1
  local iemop_stats="${IEMOP_STATS_DIR}/${project}/output/logs/report.csv"
  
  echo "iemop monitors,iemop events,iemop static,iemop dynamic,ltw monitors,ltw events,ltw static,ltw dynamic" > ${OUTPUT_DIR}/tmp-verify.csv
  for sha in $(cut -d ',' -f 1 ${iemop_stats}); do
  # IEMOP
  local iemop_test_log=${IEMOP_STATS_DIR}/${project}/output/logs/${sha}/test.log
  local iemop_monitors=$(cat ${iemop_test_log} | grep "#monitors: " | cut -d ' ' -f 2 | paste -sd+ | bc -l)
  local iemop_events=$(cat ${iemop_test_log} | grep "#event -" | cut -d ":" -f 2 | paste -sd+ | bc -l)
  local iemop_static=0
  local iemop_dynamic=0
  
  if [[ -f ${IEMOP_STATS_DIR}/${project}/output/logs/${sha}/violations/violation-counts ]]; then
    iemop_static=$(cat ${IEMOP_STATS_DIR}/${project}/output/logs/${sha}/violations/violation-counts | wc -l)
    iemop_dynamic=$(cat ${IEMOP_STATS_DIR}/${project}/output/logs/${sha}/violations/violation-counts | cut -d ' ' -f 1 | paste -sd+ | bc -l)
  fi
  
  # LTW
  local ltw_test_log=${LTW_STATS_DIR}/${project}/output/logs/${sha}/test.log
  local ltw_monitors=$(cat ${ltw_test_log} | grep "#monitors: " | cut -d ' ' -f 2 | paste -sd+ | bc -l)
  local ltw_events=$(cat ${ltw_test_log} | grep "#event -" | cut -d ":" -f 2 | paste -sd+ | bc -l)
  local ltw_static=0
  local ltw_dynamic=0
  
  if [[ -f ${LTW_STATS_DIR}/${project}/output/logs/${sha}/violations/violation-counts ]]; then
    ltw_static=$(cat ${LTW_STATS_DIR}/${project}/output/logs/${sha}/violations/violation-counts | wc -l)
    ltw_dynamic=$(cat ${LTW_STATS_DIR}/${project}/output/logs/${sha}/violations/violation-counts | cut -d ' ' -f 1 | paste -sd+ | bc -l)
  fi
  
  echo "${iemop_monitors},${iemop_events},${iemop_static},${iemop_dynamic},${ltw_monitors},${ltw_events},${ltw_static},${ltw_dynamic}" >> ${OUTPUT_DIR}/tmp-verify.csv
  done
}

function merge_csv() {
  for project in $(cat ${PROJECTS_LIST}); do
    echo "Checking ${project}"
    local paper="${PAPER_EVOLUTION_DIR}/${project}.csv"
    local timed="${IEMOP_TIMED_DIR}/${project}/output/logs/report.csv"
    
    generate_verification_csv ${project}
    
    echo "iemop jar status,iemop jar time,iemop ctw status,iemop ctw time" > ${OUTPUT_DIR}/tmp-time.csv
    cut -d ',' -f 2- ${timed} >> ${OUTPUT_DIR}/tmp-time.csv

    paste -d , ${paper} ${OUTPUT_DIR}/tmp-time.csv ${OUTPUT_DIR}/tmp-verify.csv > ${OUTPUT_DIR}/${project}.csv
  done
  
  
  rm ${OUTPUT_DIR}/tmp-verify.csv
  rm ${OUTPUT_DIR}/tmp-time.csv
}

merge_csv
