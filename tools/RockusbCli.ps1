param(
    [Parameter(Position = 0)]
    [ValidateSet('info', 'ppt', 'read-lba', 'write-lba', 'write-partition', 'download-loader', 'reset', 'test')]
    [string] $Command = 'info',

    [string] $DevicePath,
    [UInt32] $Start = 0,
    [UInt32] $Count = 0,
    [string] $Image,
    [string] $Loader,
    [string] $OutFile,
    [string] $Partition,
    [switch] $Sparse,
    [int] $ChunkSectors = 256
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$nativeCode = @'
using System;
using System.IO;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

public sealed class RockusbSession : IDisposable
{
    const uint GENERIC_READ = 0x80000000;
    const uint GENERIC_WRITE = 0x40000000;
    const uint FILE_SHARE_READ = 1;
    const uint FILE_SHARE_WRITE = 2;
    const uint OPEN_EXISTING = 3;

    const uint CBW_SIGNATURE = 0x43425355;
    const uint CSW_SIGNATURE = 0x53425355;

    const byte DIR_OUT = 0x00;
    const byte DIR_IN = 0x80;

    const byte OP_TEST_UNIT_READY = 0x00;
    const byte OP_READ_FLASH_ID = 0x01;
    const byte OP_READ_LBA = 0x14;
    const byte OP_WRITE_LBA = 0x15;
    const byte OP_READ_FLASH_INFO = 0x1A;
    const byte OP_READ_CHIP_INFO = 0x1B;
    const byte OP_DEVICE_RESET = 0xFF;

    const byte RWMETHOD_IMAGE = 0;
    const uint IOCTL_MASKROM_471 = 0x8000A000;
    const uint IOCTL_MASKROM_472 = 0x8000A004;
    const ushort CRC16_CCITT = 0x1021;

    [DllImport("kernel32.dll", SetLastError=true, CharSet=CharSet.Unicode)]
    static extern SafeFileHandle CreateFile(string name, uint access, uint share, IntPtr sec, uint creation, uint flags, IntPtr template);

    [DllImport("kernel32.dll", SetLastError=true)]
    static extern bool ReadFile(SafeFileHandle h, byte[] buf, uint size, out uint read, IntPtr ov);

    [DllImport("kernel32.dll", SetLastError=true)]
    static extern bool WriteFile(SafeFileHandle h, byte[] buf, uint size, out uint written, IntPtr ov);

    [DllImport("kernel32.dll", SetLastError=true)]
    static extern bool DeviceIoControl(SafeFileHandle h, uint code, byte[] inBuf, uint inSize, IntPtr outBuf, uint outSize, out uint bytes, IntPtr ov);

    SafeFileHandle _inPipe;
    SafeFileHandle _outPipe;

    public RockusbSession(string basePath)
    {
        _inPipe = CreateFile(basePath + "\\PIPE00", GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, IntPtr.Zero, OPEN_EXISTING, 0, IntPtr.Zero);
        if (_inPipe.IsInvalid)
            throw new InvalidOperationException("Open PIPE00 failed, Win32 error " + Marshal.GetLastWin32Error());

        _outPipe = CreateFile(basePath + "\\PIPE01", GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, IntPtr.Zero, OPEN_EXISTING, 0, IntPtr.Zero);
        if (_outPipe.IsInvalid)
            throw new InvalidOperationException("Open PIPE01 failed, Win32 error " + Marshal.GetLastWin32Error());
    }

    public void Dispose()
    {
        if (_inPipe != null) _inPipe.Dispose();
        if (_outPipe != null) _outPipe.Dispose();
    }

    static void U16BE(byte[] b, int o, ushort v)
    {
        b[o] = (byte)(v >> 8);
        b[o + 1] = (byte)v;
    }

    static void U32BE(byte[] b, int o, uint v)
    {
        b[o] = (byte)(v >> 24);
        b[o + 1] = (byte)(v >> 16);
        b[o + 2] = (byte)(v >> 8);
        b[o + 3] = (byte)v;
    }

    static void U32LE(byte[] b, int o, uint v)
    {
        b[o] = (byte)v;
        b[o + 1] = (byte)(v >> 8);
        b[o + 2] = (byte)(v >> 16);
        b[o + 3] = (byte)(v >> 24);
    }

    static uint U32LEr(byte[] b, int o)
    {
        return (uint)(b[o] | (b[o + 1] << 8) | (b[o + 2] << 16) | (b[o + 3] << 24));
    }

    static ushort U16LEr(byte[] b, int o)
    {
        return (ushort)(b[o] | (b[o + 1] << 8));
    }

    static ulong U64LEr(byte[] b, int o)
    {
        return (ulong)U32LEr(b, o) | ((ulong)U32LEr(b, o + 4) << 32);
    }

    static byte[] BuildCbw(byte opcode, uint transferLength, byte flags, byte cdbLength, uint address, ushort sectors, byte subCode)
    {
        byte[] cbw = new byte[31];
        U32LE(cbw, 0, CBW_SIGNATURE);
        U32LE(cbw, 4, NextTag());
        U32LE(cbw, 8, transferLength);
        cbw[12] = flags;
        cbw[13] = 0;
        cbw[14] = cdbLength;
        cbw[15] = opcode;
        cbw[16] = subCode;
        U32BE(cbw, 17, address);
        cbw[21] = 0;
        U16BE(cbw, 22, sectors);
        return cbw;
    }

    static uint _globalTag = 0x12340000;
    static uint NextTag()
    {
        _globalTag++;
        return _globalTag;
    }

    void WriteExact(byte[] buffer, int offset, int length)
    {
        byte[] tmp = buffer;
        if (offset != 0 || length != buffer.Length)
        {
            tmp = new byte[length];
            Buffer.BlockCopy(buffer, offset, tmp, 0, length);
        }
        uint written;
        bool ok = WriteFile(_outPipe, tmp, (uint)length, out written, IntPtr.Zero);
        if (!ok || written != length)
            throw new IOException(String.Format("WriteFile failed ok={0} written={1}/{2} err={3}", ok, written, length, Marshal.GetLastWin32Error()));
    }

    byte[] ReadExact(int length)
    {
        byte[] buffer = new byte[length];
        int total = 0;
        while (total < length)
        {
            byte[] slice = new byte[length - total];
            uint read;
            bool ok = ReadFile(_inPipe, slice, (uint)slice.Length, out read, IntPtr.Zero);
            if (!ok || read == 0)
                throw new IOException(String.Format("ReadFile failed ok={0} read={1}/{2} err={3}", ok, read, length - total, Marshal.GetLastWin32Error()));
            Buffer.BlockCopy(slice, 0, buffer, total, (int)read);
            total += (int)read;
        }
        return buffer;
    }

    void CheckCsw(byte[] csw)
    {
        if (csw.Length != 13 || U32LEr(csw, 0) != CSW_SIGNATURE)
            throw new IOException("Bad CSW signature: " + BitConverter.ToString(csw));
        if (csw[12] != 0)
            throw new IOException(String.Format("Rockusb command failed, CSW status=0x{0:X2}, residue=0x{1:X8}", csw[12], U32LEr(csw, 8)));
    }

    byte[] Command(byte opcode, uint transferLength, byte flags, byte cdbLength, uint address, ushort sectors, byte subCode, byte[] outData)
    {
        byte[] cbw = BuildCbw(opcode, transferLength, flags, cdbLength, address, sectors, subCode);
        WriteExact(cbw, 0, cbw.Length);
        if (outData != null && outData.Length > 0)
            WriteExact(outData, 0, outData.Length);

        byte[] data = new byte[0];
        if ((flags & DIR_IN) != 0 && transferLength > 0)
            data = ReadExact((int)transferLength);

        byte[] csw = ReadExact(13);
        CheckCsw(csw);
        return data;
    }

    public byte[] ReadChipInfo()
    {
        return Command(OP_READ_CHIP_INFO, 16, DIR_IN, 6, 0, 0, 0, null);
    }

    public byte[] ReadFlashId()
    {
        return Command(OP_READ_FLASH_ID, 5, DIR_IN, 6, 0, 0, 0, null);
    }

    public byte[] ReadFlashInfo()
    {
        return Command(OP_READ_FLASH_INFO, 11, DIR_IN, 6, 0, 0, 0, null);
    }

    public void TestReady()
    {
        Command(OP_TEST_UNIT_READY, 0, DIR_IN, 6, 0, 0, 0, null);
    }

    public void Reset()
    {
        Command(OP_DEVICE_RESET, 0, DIR_OUT, 6, 0, 0, 0, null);
    }

    public byte[] ReadLba(uint start, ushort sectors)
    {
        return Command(OP_READ_LBA, (uint)sectors * 512, DIR_IN, 10, start, sectors, RWMETHOD_IMAGE, null);
    }

    public void WriteLba(uint start, ushort sectors, byte[] data)
    {
        int expected = sectors * 512;
        if (data.Length != expected)
            throw new ArgumentException("Write buffer length does not match sector count");
        Command(OP_WRITE_LBA, (uint)expected, DIR_OUT, 10, start, sectors, RWMETHOD_IMAGE, data);
    }

    static ushort CrcCalculate(ushort crc, byte ch)
    {
        for (uint i = 0x80; i != 0; i >>= 1)
        {
            if ((crc & 0x8000) != 0)
                crc = (ushort)((crc << 1) ^ CRC16_CCITT);
            else
                crc = (ushort)(crc << 1);

            if ((ch & i) != 0)
                crc = (ushort)(crc ^ CRC16_CCITT);
        }
        return crc;
    }

    static ushort CrcCcitt(byte[] data, int length)
    {
        ushort crc = 0xffff;
        for (int i = 0; i < length; i++)
            crc = CrcCalculate(crc, data[i]);
        return crc;
    }

    static byte[] BuildMaskromRequest(byte[] payload, out bool sendPendingPacket)
    {
        sendPendingPacket = false;
        int dataSize = payload.Length;
        byte[] packet = new byte[dataSize + 5];
        Buffer.BlockCopy(payload, 0, packet, 0, payload.Length);

        switch (dataSize % 4096)
        {
            case 4095:
                dataSize++;
                break;
            case 4094:
                sendPendingPacket = true;
                break;
        }

        ushort crc = CrcCcitt(packet, dataSize);
        packet[dataSize] = (byte)(crc >> 8);
        packet[dataSize + 1] = (byte)crc;

        byte[] finalPacket = new byte[dataSize + 2];
        Buffer.BlockCopy(packet, 0, finalPacket, 0, finalPacket.Length);
        return finalPacket;
    }

    public static void SendMaskrom471(string basePath, byte[] payload)
    {
        SendMaskromVendorRequest(basePath, IOCTL_MASKROM_471, payload);
    }

    public static void SendMaskrom472(string basePath, byte[] payload)
    {
        SendMaskromVendorRequest(basePath, IOCTL_MASKROM_472, payload);
    }

    static void SendMaskromVendorRequest(string basePath, uint ioctl, byte[] payload)
    {
        using (SafeFileHandle h = CreateFile(basePath, GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, IntPtr.Zero, OPEN_EXISTING, 0, IntPtr.Zero))
        {
            if (h.IsInvalid)
                throw new InvalidOperationException("Open base device failed, Win32 error " + Marshal.GetLastWin32Error());

            bool sendPendingPacket;
            byte[] packet = BuildMaskromRequest(payload, out sendPendingPacket);
            int offset = 0;
            while (offset < packet.Length)
            {
                int len = Math.Min(4096, packet.Length - offset);
                byte[] chunk = new byte[len];
                Buffer.BlockCopy(packet, offset, chunk, 0, len);

                uint bytes;
                bool ok = DeviceIoControl(h, ioctl, chunk, (uint)chunk.Length, IntPtr.Zero, 0, out bytes, IntPtr.Zero);
                if (!ok)
                    throw new IOException(String.Format("DeviceIoControl 0x{0:X8} failed at {1}/{2}, err={3}", ioctl, offset, packet.Length, Marshal.GetLastWin32Error()));
                offset += len;
            }

            if (sendPendingPacket)
            {
                byte[] pending = new byte[] { 0 };
                uint bytes;
                bool ok = DeviceIoControl(h, ioctl, pending, 1, IntPtr.Zero, 0, out bytes, IntPtr.Zero);
                if (!ok)
                    throw new IOException(String.Format("DeviceIoControl 0x{0:X8} pending packet failed, err={1}", ioctl, Marshal.GetLastWin32Error()));
            }
        }
    }

    public static string Hex(byte[] data)
    {
        return BitConverter.ToString(data).Replace('-', ' ');
    }

    public static string AsciiTrim(byte[] data)
    {
        int len = 0;
        while (len < data.Length && data[len] != 0) len++;
        return System.Text.Encoding.ASCII.GetString(data, 0, len);
    }

    public static object[] ParseGpt(byte[] first34Sectors)
    {
        if (first34Sectors.Length < 34 * 512)
            throw new ArgumentException("Need at least 34 sectors");
        string sig = System.Text.Encoding.ASCII.GetString(first34Sectors, 512, 8);
        if (sig != "EFI PART")
            throw new InvalidDataException("No GPT header at LBA 1");

        ulong entriesLba = U64LEr(first34Sectors, 512 + 72);
        uint entryCount = U32LEr(first34Sectors, 512 + 80);
        uint entrySize = U32LEr(first34Sectors, 512 + 84);
        if (entriesLba != 2)
            throw new InvalidDataException("Unexpected GPT entry LBA " + entriesLba);

        int maxEntriesInBuffer = (first34Sectors.Length - 1024) / (int)entrySize;
        int count = (int)Math.Min(entryCount, (uint)maxEntriesInBuffer);
        object[] result = new object[count];
        for (int i = 0; i < count; i++)
        {
            int off = 1024 + i * (int)entrySize;
            bool empty = true;
            for (int j = 0; j < 16; j++)
            {
                if (first34Sectors[off + j] != 0) { empty = false; break; }
            }
            if (empty)
                continue;

            ulong first = U64LEr(first34Sectors, off + 32);
            ulong last = U64LEr(first34Sectors, off + 40);
            string name = System.Text.Encoding.Unicode.GetString(first34Sectors, off + 56, 72).TrimEnd('\0');
            result[i] = new PartitionInfo(name, first, last);
        }
        return result;
    }
}

public sealed class PartitionInfo
{
    public string Name { get; private set; }
    public ulong FirstLba { get; private set; }
    public ulong LastLba { get; private set; }
    public ulong SectorCount { get { return LastLba >= FirstLba ? LastLba - FirstLba + 1 : 0; } }

    public PartitionInfo(string name, ulong first, ulong last)
    {
        Name = name;
        FirstLba = first;
        LastLba = last;
    }
}
'@

if (-not ('RockusbSession' -as [type])) {
    Add-Type -TypeDefinition $nativeCode
}

function Get-RockusbDevicePath {
    if ($DevicePath) {
        return $DevicePath
    }

    $dev = Get-PnpDevice -PresentOnly |
        Where-Object { $_.InstanceId -match '^USB\\VID_2207&PID_' -and $_.FriendlyName -match 'Rockusb|USB download|Rockchip' } |
        Select-Object -First 1

    if (-not $dev) {
        throw 'No present Rockusb device found.'
    }

    $regPath = 'HKLM:\SYSTEM\CurrentControlSet\Enum\' + $dev.InstanceId + '\Device Parameters'
    $symbolic = (Get-ItemProperty -LiteralPath $regPath).SymbolicName
    if (-not $symbolic) {
        throw "No SymbolicName found for $($dev.InstanceId)"
    }

    return ($symbolic -replace '^\\\?\?\\', '\\?\')
}

function Get-RockusbSession {
    $path = Get-RockusbDevicePath
    [RockusbSession]::new($path)
}

function Get-Partitions {
    param([RockusbSession] $Session)
    $gpt = $Session.ReadLba(0, 34)
    [RockusbSession]::ParseGpt($gpt) | Where-Object { $_ -ne $null }
}

function Write-RawImageToLba {
    param(
        [RockusbSession] $Session,
        [string] $Path,
        [UInt32] $TargetLba,
        [int] $MaxSectors
    )

    $file = [IO.File]::OpenRead((Resolve-Path $Path))
    try {
        if (($file.Length % 512) -ne 0) {
            throw "Raw image length is not sector-aligned: $($file.Length)"
        }

        $buffer = New-Object byte[] ($MaxSectors * 512)
        $writtenSectors = [UInt64]0
        while ($true) {
            $read = $file.Read($buffer, 0, $buffer.Length)
            if ($read -le 0) { break }
            if (($read % 512) -ne 0) { throw 'Short raw image read was not sector-aligned.' }

            $sectors = [UInt16]($read / 512)
            if ($read -eq $buffer.Length) {
                $chunk = $buffer
            } else {
                $chunk = New-Object byte[] $read
                [Array]::Copy($buffer, 0, $chunk, 0, $read)
            }

            $Session.WriteLba(($TargetLba + [UInt32]$writtenSectors), $sectors, $chunk)
            $writtenSectors += $sectors
            if (($writtenSectors % 16384) -eq 0) {
                Write-Host ("Wrote {0:N0} sectors..." -f $writtenSectors)
            }
        }
        Write-Host ("Wrote raw image sectors: {0:N0}" -f $writtenSectors)
    } finally {
        $file.Dispose()
    }
}

function Read-Exact {
    param([IO.Stream] $Stream, [byte[]] $Buffer, [int] $Count)
    $offset = 0
    while ($offset -lt $Count) {
        $read = $Stream.Read($Buffer, $offset, $Count - $offset)
        if ($read -le 0) {
            throw 'Unexpected end of sparse image.'
        }
        $offset += $read
    }
}

function Write-SparseImageToLba {
    param(
        [RockusbSession] $Session,
        [string] $Path,
        [UInt32] $TargetLba,
        [UInt64] $PartitionSectors,
        [int] $MaxSectors
    )

    $file = [IO.File]::OpenRead((Resolve-Path $Path))
    try {
        $header = New-Object byte[] 28
        Read-Exact $file $header 28
        $magic = [BitConverter]::ToUInt32($header, 0)
        if ($magic -ne [UInt32]3978755898) {
            throw 'Sparse image magic not found.'
        }

        $fileHeaderSize = [BitConverter]::ToUInt16($header, 8)
        $chunkHeaderSize = [BitConverter]::ToUInt16($header, 10)
        $blockSize = [BitConverter]::ToUInt32($header, 12)
        $totalBlocks = [BitConverter]::ToUInt32($header, 16)
        $totalChunks = [BitConverter]::ToUInt32($header, 20)

        if (($blockSize % 512) -ne 0) {
            throw "Sparse block size is not sector-aligned: $blockSize"
        }
        if ($fileHeaderSize -gt 28) {
            $file.Position += ($fileHeaderSize - 28)
        }

        $totalSectors = ([UInt64]$totalBlocks * [UInt64]$blockSize) / 512
        if ($totalSectors -gt $PartitionSectors) {
            throw "Sparse image expands to $totalSectors sectors, larger than partition $PartitionSectors sectors."
        }

        Write-Host ("Sparse image expands to {0:N0} sectors in {1:N0} chunks." -f $totalSectors, $totalChunks)
        $outSector = [UInt64]0
        $chunkHeader = New-Object byte[] $chunkHeaderSize

        for ($chunkIndex = 0; $chunkIndex -lt $totalChunks; $chunkIndex++) {
            Read-Exact $file $chunkHeader $chunkHeaderSize
            $chunkType = [BitConverter]::ToUInt16($chunkHeader, 0)
            $chunkBlocks = [BitConverter]::ToUInt32($chunkHeader, 4)
            $chunkTotalSize = [BitConverter]::ToUInt32($chunkHeader, 8)
            $chunkBytes = [UInt64]$chunkBlocks * [UInt64]$blockSize
            $chunkSectors = [UInt64]$chunkBytes / 512

            switch ($chunkType) {
                0xCAC1 {
                    $remaining = $chunkBytes
                    while ($remaining -gt 0) {
                        $thisBytes = [int][Math]::Min([UInt64]($MaxSectors * 512), $remaining)
                        $buffer = New-Object byte[] $thisBytes
                        Read-Exact $file $buffer $thisBytes
                        $sectors = [UInt16]($thisBytes / 512)
                        $Session.WriteLba(($TargetLba + [UInt32]$outSector), $sectors, $buffer)
                        $outSector += $sectors
                        $remaining -= [UInt64]$thisBytes
                    }
                }
                0xCAC2 {
                    $fillBytes = New-Object byte[] 4
                    Read-Exact $file $fillBytes 4
                    $remaining = $chunkBytes
                    while ($remaining -gt 0) {
                        $thisBytes = [int][Math]::Min([UInt64]($MaxSectors * 512), $remaining)
                        $buffer = New-Object byte[] $thisBytes
                        for ($i = 0; $i -lt $thisBytes; $i += 4) {
                            [Array]::Copy($fillBytes, 0, $buffer, $i, [Math]::Min(4, $thisBytes - $i))
                        }
                        $sectors = [UInt16]($thisBytes / 512)
                        $Session.WriteLba(($TargetLba + [UInt32]$outSector), $sectors, $buffer)
                        $outSector += $sectors
                        $remaining -= [UInt64]$thisBytes
                    }
                }
                0xCAC3 {
                    $outSector += $chunkSectors
                }
                0xCAC4 {
                    if ($chunkTotalSize -gt $chunkHeaderSize) {
                        $file.Position += ($chunkTotalSize - $chunkHeaderSize)
                    }
                }
                default {
                    throw ("Unsupported sparse chunk type 0x{0:X4}" -f $chunkType)
                }
            }

            if (($chunkIndex % 32) -eq 0) {
                Write-Host ("Chunk {0:N0}/{1:N0}, output sector {2:N0}" -f ($chunkIndex + 1), $totalChunks, $outSector)
            }
        }

        Write-Host ("Sparse write complete: {0:N0} sectors." -f $outSector)
    } finally {
        $file.Dispose()
    }
}

function Get-RkBootEntrySet {
    param([string] $Path)

    if (-not $Path) {
        throw '-Loader is required for download-loader.'
    }

    $resolved = [string](Resolve-Path -LiteralPath $Path)
    $bytes = [IO.File]::ReadAllBytes($resolved)
    if ($bytes.Length -lt 106) {
        throw "Loader file is too small: $resolved"
    }

    $tag = [Text.Encoding]::ASCII.GetString($bytes, 0, 4)
    if ($tag -ne 'BOOT' -and $tag -ne 'LDR ') {
        throw ("Unsupported Rockchip boot tag: {0}" -f $tag)
    }

    $headerSize = [BitConverter]::ToUInt16($bytes, 4)
    $version = [BitConverter]::ToUInt32($bytes, 6)
    $chip = [Text.Encoding]::ASCII.GetString($bytes, 21, 4).TrimEnd([char]0)

    $sets = @(
        [pscustomobject]@{ Type = 1; Name = '471'; Count = [int]$bytes[25]; Offset = [int][BitConverter]::ToUInt32($bytes, 26); EntrySize = [int]$bytes[30] },
        [pscustomobject]@{ Type = 2; Name = '472'; Count = [int]$bytes[31]; Offset = [int][BitConverter]::ToUInt32($bytes, 32); EntrySize = [int]$bytes[36] }
    )

    $entries = New-Object System.Collections.Generic.List[object]
    foreach ($set in $sets) {
        if ($set.Count -le 0) { continue }
        if ($set.EntrySize -lt 57) {
            throw "Unexpected $($set.Name) entry size: $($set.EntrySize)"
        }
        if (($set.Offset + ($set.Count * $set.EntrySize)) -gt $bytes.Length) {
            throw "$($set.Name) entry table extends beyond loader file."
        }

        for ($i = 0; $i -lt $set.Count; $i++) {
            $entryOffset = $set.Offset + ($i * $set.EntrySize)
            $entryType = [BitConverter]::ToUInt32($bytes, $entryOffset + 1)
            if ($entryType -ne [uint32]$set.Type) {
                throw "Unexpected entry type at $($set.Name)[$i]: $entryType"
            }

            $entryName = [Text.Encoding]::Unicode.GetString($bytes, $entryOffset + 5, 40).TrimEnd([char]0)
            $dataOffset = [int64][BitConverter]::ToUInt32($bytes, $entryOffset + 45)
            $dataSize = [int][BitConverter]::ToUInt32($bytes, $entryOffset + 49)
            $dataDelay = [int][BitConverter]::ToUInt32($bytes, $entryOffset + 53)
            if (($dataOffset + $dataSize) -gt $bytes.Length) {
                throw "$($set.Name)[$i] data extends beyond loader file."
            }

            $data = New-Object byte[] $dataSize
            [Array]::Copy($bytes, $dataOffset, $data, 0, $dataSize)
            $entries.Add([pscustomobject]@{
                Type = $set.Type
                Index = $i
                Name = $entryName
                DataOffset = $dataOffset
                DataSize = $dataSize
                DelayMs = $dataDelay
                Data = $data
            })
        }
    }

    [pscustomobject]@{
        Path = $resolved
        Tag = $tag
        HeaderSize = $headerSize
        Version = $version
        Chip = $chip
        Entries = $entries
    }
}

function Send-RkLoaderToMaskrom {
    param(
        [string] $DeviceBasePath,
        [string] $LoaderPath
    )

    $boot = Get-RkBootEntrySet $LoaderPath
    Write-Host ("Loader: {0}, chip={1}, version=0x{2:X}, entries={3}" -f $boot.Tag, $boot.Chip, $boot.Version, $boot.Entries.Count)

    foreach ($entry in $boot.Entries | Where-Object { $_.Type -eq 1 }) {
        Write-Host ("Sending 0x471[{0}] {1}: {2:N0} bytes" -f $entry.Index, $entry.Name, $entry.DataSize)
        [RockusbSession]::SendMaskrom471($DeviceBasePath, $entry.Data)
        if ($entry.DelayMs -gt 0) {
            Start-Sleep -Milliseconds $entry.DelayMs
        }
    }

    foreach ($entry in $boot.Entries | Where-Object { $_.Type -eq 2 }) {
        Write-Host ("Sending 0x472[{0}] {1}: {2:N0} bytes" -f $entry.Index, $entry.Name, $entry.DataSize)
        [RockusbSession]::SendMaskrom472($DeviceBasePath, $entry.Data)
        if ($entry.DelayMs -gt 0) {
            Start-Sleep -Milliseconds $entry.DelayMs
        }
    }

    Start-Sleep -Seconds 1
    Write-Host 'Loader download command sequence complete.'
}

$device = Get-RockusbDevicePath
Write-Host "Rockusb device: $device"

if ($Command -eq 'download-loader') {
    Send-RkLoaderToMaskrom $device $Loader
    return
}

$session = Get-RockusbSession
try {
    switch ($Command) {
        'info' {
            $chip = $session.ReadChipInfo()
            $flashId = $session.ReadFlashId()
            $flashInfo = $session.ReadFlashInfo()
            Write-Host ("Chip info : {0} [{1}]" -f ([RockusbSession]::AsciiTrim($chip)), ([RockusbSession]::Hex($chip)))
            Write-Host ("Flash ID  : {0} [{1}]" -f ([RockusbSession]::AsciiTrim($flashId)), ([RockusbSession]::Hex($flashId)))
            Write-Host ("Flash info: {0}" -f ([RockusbSession]::Hex($flashInfo)))
        }
        'test' {
            $session.TestReady()
            Write-Host 'Device reports ready.'
        }
        'ppt' {
            Get-Partitions $session |
                Select-Object Name, FirstLba, LastLba, SectorCount |
                Format-Table -AutoSize
        }
        'read-lba' {
            if (-not $OutFile) { throw '-OutFile is required for read-lba.' }
            if ($Count -le 0 -or $Count -gt 65535) { throw '-Count must be 1..65535.' }
            $data = $session.ReadLba($Start, [UInt16]$Count)
            [IO.File]::WriteAllBytes((Join-Path (Get-Location) $OutFile), $data)
            Write-Host "Read $Count sectors from LBA $Start to $OutFile"
        }
        'write-lba' {
            if (-not $Image) { throw '-Image is required for write-lba.' }
            Write-RawImageToLba $session $Image $Start $ChunkSectors
        }
        'write-partition' {
            if (-not $Partition) { throw '-Partition is required for write-partition.' }
            if (-not $Image) { throw '-Image is required for write-partition.' }

            $part = Get-Partitions $session | Where-Object { $_.Name -eq $Partition } | Select-Object -First 1
            if (-not $part) { throw "Partition not found: $Partition" }
            if ($part.FirstLba -gt [UInt32]::MaxValue) { throw 'Partition start exceeds 32-bit LBA command range.' }

            Write-Host ("Writing partition {0}: LBA {1}, sectors {2:N0}" -f $part.Name, $part.FirstLba, $part.SectorCount)
            if ($Sparse) {
                Write-SparseImageToLba $session $Image ([UInt32]$part.FirstLba) ([UInt64]$part.SectorCount) $ChunkSectors
            } else {
                Write-RawImageToLba $session $Image ([UInt32]$part.FirstLba) $ChunkSectors
            }
        }
        'reset' {
            $session.Reset()
            Write-Host 'Reset command sent.'
        }
    }
} finally {
    $session.Dispose()
}
