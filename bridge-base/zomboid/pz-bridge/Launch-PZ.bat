@echo off
rem pz-bridge launcher: same JVM args as ProjectZomboid64.bat, but the main class
rem is dev.ermc.pzbridge.MainWrapper, which opens bridge.shm and then calls the
rem original zombie.gameStates.MainScreenState.main.
setlocal
cd /d "%~dp0..\..\.."

rem Bridge shared-memory directory (same ERMC_DIR the Minecraft side will use).
set ERMC_DIR=%CD%\runtime

set PZ_DIR=C:\SteamLibrary\steamapps\common\ProjectZomboid
set PZ_CLASSPATH=%CD%\bridge-base\zomboid\pz-bridge\build;%PZ_DIR%;%PZ_DIR%\projectzomboid.jar

pushd "%PZ_DIR%"
rem -debug keeps PZ's debug menu (cheats, god mode) alive when launched from here.
rem -XX:+UseShenandoahGC: Viewpoint's memory manager switches the game off ZGC to
rem Shenandoah and saves+quits once; mirror that switch here so relaunches go straight in.
"%PZ_DIR%\jre64\bin\java.exe" -Djava.awt.headless=true --enable-native-access=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED -Dzomboid.steam=1 -Dzomboid.znetlog=1 -XX:-CreateCoredumpOnCrash -XX:-OmitStackTraceInFastThrow -XX:+UseShenandoahGC -Xmx3072m -Djava.library.path=./win64/;./ -agentlib:zbNative -cp %PZ_CLASSPATH% dev.ermc.pzbridge.MainWrapper -debug %1 %2 %3 %4 %5
set EXITCODE=%ERRORLEVEL%
popd
echo pz-bridge: game exited with code %EXITCODE%
endlocal
