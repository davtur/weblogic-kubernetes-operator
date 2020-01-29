// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.DbUtils;
import oracle.kubernetes.operator.utils.DomainCrd;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.FmwDomain;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(Alphanumeric.class)

public class ItSoaPvWlst extends BaseTest {
  private static String rcuSchemaPrefix = "soadomain";
  private static String rcuSchemaType = "soa";
  private static String dbUrl = "oracle-db.default.svc.cluster.local:1521/devpdb.k8s";
  private static String fmwImage = "container-registry.oracle.com/middleware/soasuite:12.2.1.3";
  private static Operator operator1;
  private static String domainNS;
  private static String domainUid = "";
  private static String restartTmpDir = "";
  private static boolean testCompletedSuccessfully;
  private static String testClassName;
  private static StringBuffer namespaceList;

  /**
  * This method gets called only once before any of the test methods are executed. It does the
  * initialization of the integration test properties defined in OperatorIT.properties and setting
  * the resultRoot, pvRoot and projectRoot attributes. It also creates Oracle DB pod which used for
  * RCU.
  *
  * @throws Exception - if an error occurs when load property file or create DB pod
  */
  @BeforeAll
  public static void staticPrepare() throws Exception {
    namespaceList = new StringBuffer();
    testClassName = new Object() {
    }.getClass().getEnclosingClass().getSimpleName();
    // initialize test properties 
    initialize(APP_PROPS_FILE, testClassName);  
  }
  
  @BeforeEach
  public void prepare() throws Exception {
    if (QUICKTEST) {
      createResultAndPvDirs(testClassName);
      
      TestUtils.exec(
          "cp -rf " 
          + BaseTest.getProjectRoot() 
          + "/kubernetes/samples/scripts " 
          + getResultDir(),
          true);
      //delete leftover pods caused by test being aborted
      DbUtils.deleteRcuPod(getResultDir());
      DbUtils.stopOracleDB(getResultDir());
       
      DbUtils.startOracleDB(getResultDir());
      DbUtils.createRcuSchema(getResultDir(),rcuSchemaPrefix, rcuSchemaType, dbUrl, fmwImage);
    
      // create operator1
      if (operator1 == null) {
        Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(),
            true, testClassName);
        operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator1);
        domainNS = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList.append((String)operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS);
      }
    }  
  }
  
  @AfterEach
  public void unPrepare() throws Exception {
    DbUtils.deleteRcuPod(getResultDir());
    DbUtils.stopOracleDB(getResultDir());
  }
  
  /**
  * This method will run once after all test methods are finished. It Releases k8s cluster lease,
  * archives result, pv directories.
  *
  * @throws Exception - if any error occurs
  */
  //@AfterAll
  public static void staticUnPrepare() throws Exception {
    tearDown(new Object() {
    }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());

    LoggerHelper.getLocal().log(Level.INFO,"SUCCESS");
  }
 
  @Test
  public void testSoaDomainOnPvUsingWlst() throws Exception {
    if (QUICKTEST) {
      String testMethodName = new Object() {
      }.getClass().getEnclosingMethod().getName();
      logTestBegin(testMethodName);
      LoggerHelper.getLocal().log(Level.INFO,
          "Creating Operator & waiting for the script to complete execution");
      
      FmwDomain soadomain = null;
      boolean testCompletedSuccessfully = false;

      try {
        // create SOA domain
        Map<String, Object> domainMap = createDomainMap(getNewSuffixCount(), testClassName);
        domainMap.put("namespace", domainNS);
        domainMap.put("initialManagedServerReplicas", new Integer("2"));
        domainMap.put("image", fmwImage);
        //domainMap.put("image", "container-registry.oracle.com/middleware/soasuite:12.2.1.3");
        LoggerHelper.getLocal().log(Level.INFO,
             "SOA IMAGE USED IS: " + (String)domainMap.get("image"));
        domainMap.put("clusterName", "soa-cluster");
        domainMap.put("managedServerNameBase", "soaserver");
        domainMap.put("rcuSchemaPrefix", rcuSchemaPrefix);
        domainMap.put("rcuDatabaseURL", dbUrl);
        domainMap.put("fmwType", "soa");
        domainUid = (String) domainMap.get("domainUID");
        LoggerHelper.getLocal().log(Level.INFO,
            "Creating and verifying the domain creation with domainUid: " + domainUid);

        soadomain = new FmwDomain(domainMap);
        soadomain.verifyDomainCreated();
        
        // basic test cases
        testBasicUseCases(soadomain, false);

        testCompletedSuccessfully = true;
      } finally {
        if (soadomain != null  && (JENKINS || testCompletedSuccessfully)) {
          soadomain.shutdownUsingServerStartPolicy();
        }
      }

      LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
    }
  }
}