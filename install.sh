apt install openjdk-11-jre-headless
apt install openjdk-11-jdk-headless
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add
apt-get update
apt-get install sbt
wget https://github.com/llvm/circt/releases/download/firtool-1.42.0/firrtl-bin-ubuntu-20.04.tar.gz
tar -xvzf firrtl-bin-ubuntu-20.04.tar.gz
export PATH=$PWD/firtool-1.42.0/bin/:$PATH
