#!/bin/bash

SCRIPT_DIR=$( cd $( dirname $0 ) && pwd )
IEMOP_DIR="${SCRIPT_DIR}/iemop"

function clone_repository() {
  echo "Cloning iemop repository"
  pushd ${SCRIPT_DIR}
  git clone https://github.com/pokequestionableboss/imop iemop
  popd
}

function build_extension() {
  echo "Building javamop extension"
  pushd ${IEMOP_DIR}/extensions/javamop-extension
  mvn package
  cp target/javamop-extension-1.0.jar ${IEMOP_DIR}/extensions/
  popd
  
  echo "Building emop extension"
  pushd ${IEMOP_DIR}/extensions/emop-extension
  mvn package
  cp target/emop-extension-1.0.jar ${IEMOP_DIR}/extensions/
  popd
  
  echo "Building iemop extension"
  pushd ${IEMOP_DIR}/extensions/iemop-maven-extension
  mvn package
  cp target/iemop-maven-extension-1.0.jar ${IEMOP_DIR}/extensions/
  popd
  
  echo "Building disable-plugins extension"
  pushd ${IEMOP_DIR}/extensions/disable-plugins-extension
  mvn package
  cp target/disable-plugins-extension-1.0.jar ${IEMOP_DIR}/extensions/
  popd
}

function install_emop() {
  mkdir -p ${HOME}/dependencies/repo

  if [[ ! -d ${HOME}/dependencies/starts ]]; then
    # Install STARTS (Source: https://github.com/TestingResearchIllinois/starts)
    pushd ${HOME}/dependencies &> /dev/null
    git clone https://github.com/TestingResearchIllinois/starts
    if [[ $? -ne 0 ]]; then
      echo "Cannot clone STARTS"
      exit 1
    fi
    popd &> /dev/null
  fi
  
  pushd ${HOME}/dependencies/starts &> /dev/null
  git checkout impacted-both-ways
  mvn -Dmaven.repo.local=${HOME}/dependencies/repo -DskipTests -Dinvoker.skip install
  if [[ $? -ne 0 ]]; then
    echo "Cannot install STARTS"
    exit 1
  fi
  popd &> /dev/null

  if [[ ! -d ${HOME}/dependencies/emop ]]; then
    pushd ${HOME}/dependencies &> /dev/null
    # Install eMOP (Source: https://github.com/SoftEngResearch/emop)
    git clone https://github.com/SoftEngResearch/emop
    if [[ $? -ne 0 ]]; then
      echo "Cannot clone eMOP"
      exit 1
    fi
    popd &> /dev/null
  fi
  
  pushd ${HOME}/dependencies/emop &> /dev/null
  rm -f emop-maven-plugin/src/main/resources/weaved-specs/Object_NoCloneMonitorAspect.aj
  
  # Fix Arrays_SortBeforeBinarySearch_modify NPE when arr is null
  sed -i 's/pointcut Arrays_SortBeforeBinarySearch_modify(Object\[\] arr) : (set(Object\[\] \*) && args(arr)) && MOP_CommonPointCut();/pointcut Arrays_SortBeforeBinarySearch_modify(Object\[\] arr) : (set(Object\[\] \*) \&\& args(arr) \&\& if(arr != null)) \&\& MOP_CommonPointCut();/' emop-maven-plugin/src/main/resources/weaved-specs/Arrays_SortBeforeBinarySearchMonitorAspect.aj

  cp ${HOME}/iemop/mop/Arrays_MutuallyComparableMonitorAspect.aj emop-maven-plugin/src/main/resources/weaved-specs/Arrays_MutuallyComparableMonitorAspect.aj  # Fix NPE when array contains null element

  mvn -Dmaven.repo.local=${HOME}/dependencies/repo install
  if [[ $? -ne 0 ]]; then
    echo "Cannot install eMOP"
    exit 1
  fi
  popd &> /dev/null
}

function setup() {
  clone_repository
  build_extension
  install_emop
}

setup
