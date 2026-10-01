' Double-click this (or a shortcut to it) to open the Inventory app
' silently, with no black console window flashing on screen.
'
' Keep this .vbs in the SAME folder as RunInventoryApp.bat

Set objShell = CreateObject("WScript.Shell")
strFolder = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
objShell.CurrentDirectory = strFolder
objShell.Run """" & strFolder & "\RunInventoryApp.bat""", 0, False
