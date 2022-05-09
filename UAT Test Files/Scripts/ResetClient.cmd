@echo this resets an installed client to a clean state, data will be lost.

@echo Stopping Service
sc stop Code42Service

timeout 5

@echo Killing the desktop (if it's running)
taskkill /IM SharePlanDesktop.exe /F

@echo deleting the shareplan library
del /Q "%APPDATA%\Microsoft\Windows\Libraries\SharePlan.library-ms"

@deleting directories
RMDIR /Q /S "%USERPROFILE%\SharePlan"
RMDIR /Q /S "%LOCALAPPDATA%\Code 42 Software"

@echo deleting registry keys
if exist %WINDIR%\sysnative\reg.exe (
		%WINDIR%\sysnative\reg.exe delete "HKLM\Software\Code 42 Software\Common" /v UserName /f
		%WINDIR%\sysnative\reg.exe delete "HKLM\Software\Code 42 Software\Common" /v User /f 
		%WINDIR%\sysnative\reg.exe delete "HKLM\Software\Code 42 Software\Common" /v DeviceID /f 
		%WINDIR%\sysnative\reg.exe delete "HKLM\Software\Code 42 Software\Common" /v ClockOffset /f 
		%WINDIR%\sysnative\reg.exe delete "HKLM\Software\Code 42 Software\Common" /v Version /f
		%WINDIR%\sysnative\reg.exe add "HKLM\Software\Code 42 Software\Common" /v LogMode /f /d "Full"
) else (
		reg delete "HKLM\Software\Code 42 Software\Common" /v UserName /f
		reg delete "HKLM\Software\Code 42 Software\Common" /v User /f 
		reg delete "HKLM\Software\Code 42 Software\Common" /v DeviceID /f 
		reg delete "HKLM\Software\Code 42 Software\Common" /v ClockOffset /f 
		reg delete "HKLM\Software\Code 42 Software\Common" /v Version /f
		reg add "HKLM\Software\Code 42 Software\Common" /v LogMode /f /d "Full"
)

@echo restarting the service
sc start Code42Service

@echo Done.



