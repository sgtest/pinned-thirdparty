rm -rf build
mkdir build
javac -source 1.4 -d build `find . -name "*.java"`
cd build
jar cvf ../openide-4.0.jar . 
