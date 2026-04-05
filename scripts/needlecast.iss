; Needlecast — Inno Setup installer script
;
; Wraps the jpackage app-image (bundled JRE + exe) into a single .exe installer.
;
; Compile locally:
;   iscc /DAppVersion=0.7.0 scripts\needlecast.iss
;
; In CI the build-jpackage.ps1 script calls this automatically.
; The AppVersion define is injected by the script; do not hardcode it here.

#ifndef AppVersion
  #define AppVersion "0.0.0-dev"
#endif

#define AppName      "Needlecast"
#define AppPublisher "rygel"
#define AppURL       "https://github.com/rygel/needlecast"
#define AppExeName   "Needlecast.exe"
; Path to the jpackage app-image directory, relative to this .iss file
#define AppImageDir  "..\build\jpackage\Needlecast"
#define OutputDir    "..\build"

[Setup]
; ── Identity ──────────────────────────────────────────────────────────────────
; AppId MUST stay constant across all versions so Windows recognises updates
; and the uninstaller entry is updated rather than duplicated.
AppId={{E4B2C1A3-7F56-4D89-B0E2-3A9C5F1D8726}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}/issues
AppUpdatesURL={#AppURL}/releases

; ── Installation ──────────────────────────────────────────────────────────────
; PrivilegesRequired=lowest means the installer runs without UAC elevation.
; On first install the user can elevate if they want Program Files placement;
; /PASSIVE updates use the same privilege level as the original install.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
DefaultDirName={localappdata}\Programs\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes

; ── Output ────────────────────────────────────────────────────────────────────
OutputDir={#OutputDir}
OutputBaseFilename=needlecast-{#AppVersion}-win64
SetupIconFile=..\needlecast-desktop\src\main\resources\icons\needlecast.ico

; ── Compression ───────────────────────────────────────────────────────────────
Compression=lzma2/ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes

; ── Appearance ────────────────────────────────────────────────────────────────
WizardStyle=modern
WizardSmallImageFile=..\needlecast-desktop\src\main\resources\icons\installer-small.bmp

; ── Versioning ────────────────────────────────────────────────────────────────
VersionInfoVersion={#AppVersion}
VersionInfoCompany={#AppPublisher}
VersionInfoDescription={#AppName} Setup
VersionInfoProductName={#AppName}
VersionInfoProductVersion={#AppVersion}

; ── Architecture ──────────────────────────────────────────────────────────────
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Recursively include the entire jpackage app-image (exe + bundled JRE + app jar)
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}";                       Filename: "{app}\{#AppExeName}"
Name: "{group}\{cm:UninstallProgram,{#AppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}";                 Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
; Offer to launch after install (skipped when running /PASSIVE or /SILENT)
Filename: "{app}\{#AppExeName}"; \
  Description: "{cm:LaunchProgram,{#StringChange(AppName, '&', '&&')}}"; \
  Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Remove the config directory when uninstalling (optional — comment out to preserve user data)
; Type: filesandordirs; Name: "{localappdata}\Needlecast"
