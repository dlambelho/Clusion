#!/usr/bin/bash

docker run -dt -p 1521:1521 -p 5500:5500 -v ~/system_tests/db_scripts:/container-entrypoint-initdb.d -e "ORACLE_PASSWORD=oracle" --name "oracle_exp" --rm gvenzl/oracle-xe:21-slim-faststart &&  \
sleep 30 && \
~/system_tests/testBXT.sh > /tmp/d.lambelho.files/BXT_results && \
~/system_tests/testBIEX.sh > /tmp/d.lambelho.files/BIEX_results

