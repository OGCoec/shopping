@echo off
set ORDER_LOADTEST_BYPASS_GUARDS=true
set APP_OUTBOX_FAULT_ENABLED=false
set FAULT_PROB_ORDER_STOCK_DEDUCT=0.0
set FAULT_PROB_ACCOUNT_STATUS_SYNC=0.0
cd /d C:\Users\damn\Desktop\shopping
"C:\Program Files\Apache\maven\apache-maven-3.9.9\bin\mvn.cmd" -pl shopping-common,shopping-model,shopping-mapper,shopping-service install -DskipTests -o -Dmaven.repo.local=%USERPROFILE%\.m2\repository >> C:\Users\damn\Desktop\shopping\app-startup.log 2>&1
"C:\Program Files\Apache\maven\apache-maven-3.9.9\bin\mvn.cmd" -pl shopping-web spring-boot:run -DskipTests -o -Dmaven.repo.local=%USERPROFILE%\.m2\repository >> C:\Users\damn\Desktop\shopping\app-startup.log 2>&1
