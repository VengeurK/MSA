cd ..

wget http://files.minecraftforge.net/maven/net/minecraftforge/forge/1.12.2-14.23.2.2611/forge-1.12.2-14.23.2.2611-mdk.zip -O ../forge.zip
unzip ../forge.zip -d ../
rm ../forge.zip

cp -r ../gradle .
cp ../gradlew .
cp ../gradlew.bat .
cp ../build.gradle .

./gradlew setupDecompWorkspace
./gradlew eclipse

ln -s ../src/main/python run/python

cd src