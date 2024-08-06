#!/bin/bash
#######################################
# Some projects cannot work right out of the box.
# This function preprocesses certain specified projects.
# Arguments:
#   Name of the project.
#######################################

function treat_special() {
  local project_name=$1
  local sha=$2
  
  if [[ ${project_name} == "JodaOrg/joda-time" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    find -name TestDateTimeComparator.java | xargs rm -f
    find -name TestAll.java | xargs sed -i 's/.*TestDateTimeComparator.*//g'
  fi
  if [[ "${project_name}" == "addthis/stream-lib" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    find -name TDigestTest.java | xargs rm -f
  fi
  if [[ ${project_name} == "imglib/imglib2" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    git checkout pom.xml
    cp pom.xml pom.xml.bak
    head -n -1 pom.xml.bak > pom.xml
    echo "	<build>
	<plugins>
	  <plugin>
	    <groupId>org.apache.maven.plugins</groupId>
	    <artifactId>maven-surefire-plugin</artifactId>
	    <version>2.22.1</version>
	    <configuration>
	      <argLine>-Xms20g -Xmx20g</argLine>
	    </configuration>
	  </plugin>
	</plugins>
	</build>
" >> pom.xml
    tail -1 pom.xml.bak >> pom.xml
  fi
  if [[ ${project_name} == "apache/commons-imaging" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    find -name ByteSourceImageTest.java | xargs rm -f
    find -name BitmapRoundtripTest.java | xargs rm -f
    find -name GrayscaleRountripTest.java | xargs rm -f
    find -name LimitedColorRoundtripTest.java | xargs rm -f
  fi
  if [[ ${project_name} == "apache/commons-lang" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    find -name FastDateFormatTest.java | xargs rm -f
    find -name EventListenerSupportTest.java | xargs rm -f
    find -name EventUtilsTest.java | xargs rm -f
    find -name StrTokenizerTest.java | xargs rm -f
  fi
  if [[ ${project_name} == "apache/commons-dbcp" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    find -name TestManagedDataSourceInTx.java | xargs rm -f
    find -name TestDriverAdapterCPDS.java | xargs rm -f
    find -name TestAbandonedBasicDataSource.java | xargs rm -f
    find -name TestPerUserPoolDataSource.java | xargs rm -f
  fi
  if [[ ${project_name} == "apache/commons-io" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    find -name ValidatingObjectInputStreamTest.java | xargs rm -f
    find -name FileCleaningTrackerTestCase.java | xargs rm -f
    find -name FileCleanerTestCase.java | xargs rm -f
    sed -i 's/Xmx25M/Xmx8000M/' pom.xml
  fi
  if [[ ${project_name} == "jnr/jnr-posix" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    sed -i 's/2.2.11-SNAPSHOT/2.2.11/g' pom.xml
  fi
  if [[ ${project_name} == "pagehelper/Mybatis-PageHelper" ]]; then
    if [[ ${sha} == "0f7bdcd06fdd05b0159a205987fd1cc38bab3911" ]]; then
      echo "[treat_special] Fixing ${project_name}"
      # com.github.pagehelper.util.SqlSafeUtilTest only has 1 test case
      find -name SqlSafeUtilTest.java | xargs rm -f
    fi
  fi
  if [[ ${project_name} == "sigopt/sigopt-java" ]]; then
    if [[ ${sha} == "9430f0c7b202e3b8c0db075eea217a67a49771d6" ]]; then
      echo "[treat_special] Fixing ${project_name}"
      # com.sigopt.model.AssignmentsTest only has 1 test case
      find -name AssignmentsTest.java | xargs rm -f
    fi
    
    if [[ ${sha} == "1ff6ad702bf4e534be62f13d662d8b1683e770fd" ]]; then
      echo "[treat_special] Fixing ${project_name}"
      # com.sigopt.net.HeadersBuilderTest has 7 test cases
      find -name HeadersBuilderTest.java | xargs rm -f
    fi
  fi
  if [[ ${project_name} == "Mastercard/client-encryption-java" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    # Disable flaky tests
    for file in $(find -name OkHttpJweInterceptorTest.java); do
      git checkout ${file}
      sed -i '/testIntercept_ShouldDecryptResponsePayloadAndUpdateContentLengthHeader/i\@org.junit.Ignore' ${file}
      sed -i '/testInterceptResponse_ShouldDecryptWithA128CBC_HS256Encryption/i\@org.junit.Ignore' ${file}
    done
    
    for file in $(find -name OpenFeignJweDecoderTest.java); do
      git checkout ${file}
      sed -i '/testDecode_ShouldDecryptResponsePayloadAndUpdateContentLengthHeader/i\@org.junit.Ignore' ${file}
      sed -i '/testDecode_ShouldDecryptWithA128CBC_HS256Encryption/i\@org.junit.Ignore' ${file}
    done
    
    for file in $(find -name OkHttp2JweInterceptorTest.java); do
      git checkout ${file}
      sed -i '/testIntercept_ShouldDecryptResponsePayloadAndUpdateContentLengthHeader/i\@org.junit.Ignore' ${file}
      sed -i '/testInterceptResponse_ShouldDecryptWithA128CBC_HS256Encryption/i\@org.junit.Ignore' ${file}
    done
    
    for file in $(find -name HttpExecuteJweInterceptorTest.java); do
      git checkout ${file}
      sed -i '/testInterceptResponse_ShouldDecryptResponsePayloadAndUpdateContentLengthHeader/i\@org.junit.Ignore' ${file}
      sed -i '/testInterceptResponse_ShouldDecryptWithA128CBC_HS256Encryption/i\@org.junit.Ignore' ${file}
    done
  fi
  if [[ ${project_name} == "GoogleCloudPlatform/kafka-pubsub-emulator" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    # com.google.cloud.partners.pubsub.kafka.AdminImplTest has 2 test cases
    find -name AdminImplTest.java | xargs rm -f
  fi
  if [[ ${project_name} == "jdbc-observations/datasource-proxy" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    # net.ttddyy.dsproxy.listener.logging.SLF4JSlowQueryListenerTest has 2 test cases
    find -name SLF4JSlowQueryListenerTest.java | xargs rm -f
    # net.ttddyy.dsproxy.listener.logging.CommonsSlowQueryListenerTest has 1 test case
    find -name CommonsSlowQueryListenerTest.java | xargs rm -f
  fi
  if [[ ${project_name} == "BingAds/BingAds-Java-SDK" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    # com.microsoft.bingads.v12.api.test.entities.ad_group_dsa_target.BulkAdGroupDynamicSearchAdTargetTests has 67 test cases
    find -name BulkAdGroupDynamicSearchAdTargetTests.java | xargs rm -f
    # com.microsoft.bingads.v12.api.test.entities.ad_group_dsa_target.BulkAdGroupDynamicSearchAdTargetWriteTests has 33 test cases
    find -name BulkAdGroupDynamicSearchAdTargetWriteTests.java | xargs rm -f
  fi
  if [[ ${project_name} == "datastax/native-protocol" ]]; then
    echo "[treat_special] Fixing ${project_name}"
    if [[ ! -f ${MAVEN_HOME}/lib/ext/${EXTENSION_PATH}/disable-plugins-extension-1.0.jar ]]; then
      cp ${EXTENSION_PATH}/disable-plugins-extension-1.0.jar ${MAVEN_HOME}/lib/ext/
    fi
  fi
}
