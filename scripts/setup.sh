#!/bin/bash

SCRIPT_DIR=$(cd $(dirname $0) && pwd)

function setup() {
  cd ${SCRIPT_DIR}
  
  log "Setting up environment..."
  log "Script version: $(git rev-parse HEAD)"
  
  local should_copy_repo=true
  if [[ -d ${REPO_DIR} ]]; then
    log "Will not copy repo"
    should_copy_repo=false
  fi
  
  # Clone project
  pushd ${OUTPUT_DIR} &> /dev/null
  git clone https://github.com/${REPO} project-prep &> ${LOG_DIR}/clone.log
  if [[ $? -ne 0 ]]; then
    log "Cannot clone project"
    exit 1
  fi
  
  clone_all
  popd &> /dev/null

  log "Setting up iemop"
  setup_iemop

  log "Setting up mop"
  setup_mop

  if [[ ${should_copy_repo} == "true" ]]; then
    copy_repo
  else
    setup_iemop_all
  fi
}

function clone_all() {
  if [[ ${VARIANT} == "all" ]]; then
    for run_variant in ${all_variants[@]}; do
      git clone project-prep project-${run_variant} &> /dev/null
    done
  elif [[ ${VARIANT} == "cia" ]]; then
    for run_variant in ${cia_variants[@]}; do
      git clone project-prep project-${run_variant} &> /dev/null
    done
  elif [[ ${VARIANT} == "emop" ]]; then
    for run_variant in ${emop_variants[@]}; do
      git clone project-prep project-${run_variant} &> /dev/null
    done
  elif [[ ${VARIANT} == "iemop" ]]; then
    for run_variant in ${iemop_variants[@]}; do
      git clone project-prep project-${run_variant} &> /dev/null
    done
  else
    git clone project-prep project-${VARIANT} &> /dev/null
  fi
}

function copy_repo() {
  if [[ ${VARIANT} == "all" ]]; then
    for run_variant in ${all_variants[@]}; do
      if [[ ${run_variant} == "ps1c" || ${run_variant} == "ps3cl" ]]; then
        cp -r ${HOME}/dependencies/repo ${REPO_DIR}-${run_variant}
        mvn -Dmaven.repo.local=${REPO_DIR}-${run_variant} install:install-file -Dfile=${SCRIPT_DIR}/../mop/no-track-agent.jar -DgroupId="javamop-agent-${run_variant}" -DartifactId="javamop-agent-${run_variant}" -Dversion="1.0" -Dpackaging="jar"
      elif [[ ${run_variant} != "test" && ${run_variant} != "mop" && ${run_variant} != "mopCache" && ${run_variant} != "mopCacheShared" ]]; then
        cp -r ${REPO_DIR}-iemop ${REPO_DIR}-${run_variant}
      else
        mkdir -p ${REPO_DIR}-${run_variant}
      fi
    done
  elif [[ ${VARIANT} == "cia" ]]; then
    for run_variant in ${cia_variants[@]}; do
      cp -r ${REPO_DIR}-iemop ${REPO_DIR}-${run_variant}
    done
  elif [[ ${VARIANT} == "emop" ]]; then
    for run_variant in ${emop_variants[@]}; do
      cp -r ${HOME}/dependencies/repo ${REPO_DIR}-${run_variant}
      mvn -Dmaven.repo.local=${REPO_DIR}-${run_variant} install:install-file -Dfile=${SCRIPT_DIR}/../mop/no-track-agent.jar -DgroupId="javamop-agent-${run_variant}" -DartifactId="javamop-agent-${run_variant}" -Dversion="1.0" -Dpackaging="jar"
    done
  elif [[ ${VARIANT} == "iemop" ]]; then
    for run_variant in ${iemop_variants[@]}; do
      if [[ ${run_variant} == "ps1c" || ${run_variant} == "ps3cl" ]]; then
        cp -r ${HOME}/dependencies/repo ${REPO_DIR}-${run_variant}
        mvn -Dmaven.repo.local=${REPO_DIR}-${run_variant} install:install-file -Dfile=${SCRIPT_DIR}/../mop/no-track-agent.jar -DgroupId="javamop-agent-${run_variant}" -DartifactId="javamop-agent-${run_variant}" -Dversion="1.0" -Dpackaging="jar"
      elif [[ ${run_variant} != "test" && ${run_variant} != "mop" && ${run_variant} != "mopCache" && ${run_variant} != "mopCacheShared" ]]; then
        cp -r ${REPO_DIR}-iemop ${REPO_DIR}-${run_variant}
      else
        mkdir -p ${REPO_DIR}-${run_variant}
      fi
    done
  else
    if [[ ${VARIANT} == "ps1c" || ${VARIANT} == "ps3cl" ]]; then
      cp -r ${HOME}/dependencies/repo ${REPO_DIR}-${VARIANT}
      mvn -Dmaven.repo.local=${REPO_DIR}-${VARIANT} install:install-file -Dfile=${SCRIPT_DIR}/../mop/no-track-agent.jar -DgroupId="javamop-agent-${VARIANT}" -DartifactId="javamop-agent-${VARIANT}" -Dversion="1.0" -Dpackaging="jar"
    elif [[ ${VARIANT} != "test" && ${VARIANT} != "mop" && ${VARIANT} != "mopCache" && ${VARIANT} != "mopCacheShared" ]]; then
      cp -r ${REPO_DIR}-iemop ${REPO_DIR}-${VARIANT}
    else
      mkdir -p ${REPO_DIR}-${run_variant}
    fi
  fi
  
  rm -rf ${REPO_DIR}-iemop
}

function setup_iemop() {
  # Install iemop plugin
  pushd ${SCRIPT_DIR}/../maven-plugin &> /dev/null
  mvn -Dmaven.repo.local=${REPO_DIR}-iemop install &> ${LOG_DIR}/install.log
  if [[ $? -ne 0 ]]; then
    log "Cannot install plugin"
    exit 1
  fi
  popd &> /dev/null
  
  if [[ ${STATS} == "true" ]]; then
    # Setup to collect stats
    log "Install pre-instrumented surefire booter"
    mvn install:install-file -Dmaven.repo.local=${REPO_DIR}-iemop -Dfile=${SCRIPT_DIR}/../experiments/instrumented-surefire/surefire-booter-3.1.2.jar -DpomFile=${SCRIPT_DIR}/../experiments/instrumented-surefire/pom.xml -DgroupId="org.apache.maven.surefire" -DartifactId="surefire-booter" -Dversion="3.1.2" -Dpackaging="jar" &> /dev/null
    
    # Use extension to force the project to use surefire 3.1.2
    export SHOW_STATS=1
  fi
}

function setup_iemop_all() {
  pushd ${SCRIPT_DIR}/../maven-plugin &> /dev/null
  if [[ ${VARIANT} == "all" ]]; then
    for run_variant in ${all_variants[@]}; do
      if [[ ${run_variant} == "ps1c" || ${run_variant} == "ps3cl" ]]; then
        mvn -Dmaven.repo.local=${REPO_DIR}-${run_variant} install:install-file -Dfile=${SCRIPT_DIR}/../mop/no-track-agent.jar -DgroupId="javamop-agent-${run_variant}" -DartifactId="javamop-agent-${run_variant}" -Dversion="1.0" -Dpackaging="jar"
      fi
      if [[ ${run_variant} != "ps1c" && ${run_variant} != "ps3cl" && ${run_variant} != "test" && ${run_variant} != "mop" && ${run_variant} != "mopCache" && ${run_variant} != "mopCacheShared" ]]; then
        mvn -Dmaven.repo.local=${REPO_DIR}-iemop install &> ${LOG_DIR}/install-${run_variant}.log
      fi
    done
  elif [[ ${VARIANT} == "cia" ]]; then
    for run_variant in ${cia_variants[@]}; do
      if [[ ${run_variant} != "ps1c" && ${run_variant} != "ps3cl" && ${run_variant} != "test" && ${run_variant} != "mop" && ${run_variant} != "mopCache" && ${run_variant} != "mopCacheShared" ]]; then
        mvn -Dmaven.repo.local=${REPO_DIR}-iemop install &> ${LOG_DIR}/install-${run_variant}.log
      fi
    done
  elif [[ ${VARIANT} == "emop" ]]; then
    if [[ ${run_variant} == "ps1c" || ${run_variant} == "ps3cl" ]]; then
      mvn -Dmaven.repo.local=${REPO_DIR}-${run_variant} install:install-file -Dfile=${SCRIPT_DIR}/../mop/no-track-agent.jar -DgroupId="javamop-agent-${run_variant}" -DartifactId="javamop-agent-${run_variant}" -Dversion="1.0" -Dpackaging="jar"
    fi
  elif [[ ${VARIANT} == "iemop" ]]; then
    for run_variant in ${iemop_variants[@]}; do
      if [[ ${run_variant} == "ps1c" || ${run_variant} == "ps3cl" ]]; then
        mvn -Dmaven.repo.local=${REPO_DIR}-${run_variant} install:install-file -Dfile=${SCRIPT_DIR}/../mop/no-track-agent.jar -DgroupId="javamop-agent-${run_variant}" -DartifactId="javamop-agent-${run_variant}" -Dversion="1.0" -Dpackaging="jar"
      fi
      if [[ ${run_variant} != "ps1c" && ${run_variant} != "ps3cl" && ${run_variant} != "test" && ${run_variant} != "mop" && ${run_variant} != "mopCache" && ${run_variant} != "mopCacheShared" ]]; then
        mvn -Dmaven.repo.local=${REPO_DIR}-iemop install &> ${LOG_DIR}/install-${run_variant}.log
      fi
    done
  else
    if [[ ${VARIANT} == "ps1c" || ${VARIANT} == "ps3cl" ]]; then
      mvn -Dmaven.repo.local=${REPO_DIR}-${VARIANT} install:install-file -Dfile=${SCRIPT_DIR}/../mop/no-track-agent.jar -DgroupId="javamop-agent-${VARIANT}" -DartifactId="javamop-agent-${VARIANT}" -Dversion="1.0" -Dpackaging="jar"
    fi
    if [[ ${VARIANT} != "ps1c" && ${VARIANT} != "ps3cl" && ${VARIANT} != "test" && ${VARIANT} != "mop" && ${VARIANT} != "mopCache" && ${VARIANT} != "mopCacheShared" ]]; then
      mvn -Dmaven.repo.local=${REPO_DIR}-iemop install &> ${LOG_DIR}/install-${VARIANT}.log
    fi
  fi
  
  popd &> /dev/null
}

function setup_mop() {
  mvn -Dmaven.repo.local=${REPO_DIR}-mop install:install-file -Dfile=${SCRIPT_DIR}/../mop/no-track-agent.jar -DgroupId="javamop-agent" -DartifactId="javamop-agent" -Dversion="1.0" -Dpackaging="jar"
  
  if [[ ! -f ${SCRIPT_DIR}/../mop/no-track-cache-agent.jar ]]; then
    log "Generating cache agent"
    
    pushd ${SCRIPT_DIR}/../mop &> /dev/null
    cp no-track-agent.jar no-track-cache-agent.jar
    javac -cp no-track-agent.jar DefaultCacheKeyResolver.java
    mkdir -p org/aspectj/weaver/tools/cache/
    cp DefaultCacheKeyResolver.class org/aspectj/weaver/tools/cache/
    jar -uf no-track-cache-agent.jar -C . org/aspectj/weaver/tools/cache/DefaultCacheKeyResolver.class
    popd &> /dev/null
  fi
  
  mvn -Dmaven.repo.local=${REPO_DIR}-mopCache install:install-file -Dfile=${SCRIPT_DIR}/../mop/no-track-cache-agent.jar -DgroupId="javamop-agent" -DartifactId="javamop-agent" -Dversion="1.0" -Dpackaging="jar"
  
  mvn -Dmaven.repo.local=${REPO_DIR}-mopCacheShared install:install-file -Dfile=${SCRIPT_DIR}/../mop/no-track-cache-agent.jar -DgroupId="javamop-agent" -DartifactId="javamop-agent" -Dversion="1.0" -Dpackaging="jar"
}
