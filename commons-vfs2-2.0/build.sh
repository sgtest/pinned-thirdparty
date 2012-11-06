cd core
mvn    -Dmaven.test.skip=true install
cd ..
cd sandbox
mvn    -Dmaven.test.skip=true install
cd ..
cp ./sandbox/target/commons-vfs2-sandbox-2.0.jar .
cp ./core/target/commons-vfs2-2.0.jar .

