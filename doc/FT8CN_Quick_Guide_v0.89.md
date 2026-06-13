# FT8CN Quick Guide v0.89

## 第 1 页

By NØBOY                                                                                  
https://www.qrz.com/db/n0boy                                                    
https://github.com/N0BOY/FT8CN
What is FT8CN?
FT8CN is a mobile FT8 communication software based on Android system developed by 
BG7YOZ. The FT8CN is developed for easy mobile operation, especially outdoors and 
can be used at any time FT8 contact. The operation logic is as simple as possible, easy to 
use, with the basic operation of FT8 Function.
What is the minimum Android version to run FT8CN?
The minimum version of Android 6.0 is required for FT8CN operation (compatible with
Hongmeng system). For a better experience, it is recommended that you try to use
Use newer hardware and operating systems.
Using FT8CN for the first time
To use FT8CN for the first time, you need to
set up your callsign and all In the
Maidenhead grid.


## 第 2 页

What permissions does FT8CN need?
FT8CN requires the most basic permissions:
 Recording (for collecting radio sound)⚫
The following permissions are not required:
 Positioning (used to automatically determine the grid where the station is located)⚫
 Bluetooth (for radios with Bluetooth modules)⚫
Some devices even give recording permission when they are running for the first time※
It may not be able to record, you need to restart FT8CN to record.
To determine whether the FT8CN can record normally, you can go to the spectrum 
interface to view the spectrum. The following picture points Especially the spectrum that 
cannot be recorded and can be recorded normally:


## 第 3 页




## 第 4 页

How does FT8CN control the radio?
FT8CN can control the computer through various methods such as voice control, USB 
serial port, bluetooth, network, etc. tower.
The radio stations currently supporting Bluetooth control are: GUOHE Q900
Currently, the radio stations that support network mode include: some models of ICom, 
FlexRadio 6000 series List.
How to connect FT8CN with USB cable?
At present, many radio stations support USB serial ports and USB virtual sound cards.
Connecting to FT8CN will have a good pickup effect, and the radio station can be 
controlled by CAT
PTT, sync frequency, operation mode, etc. (SWR and ALC report are also supported for 
some models policy).
Use the USB serial port to control the radio, please make sure the phone supports OTG or
use OTG transfer head.
Before connecting the radio, please select your corresponding radio model and suitable
baud rate, the different radio control commands are are not identical, selection errors may 
be uncontrollable radio station.
When the radio’s USB cable is connected to the phone After that, a serial port 
authorization prompt will pop up, please select "FT8CN".
At this time, the operating system will automatically open FT8CN, and a serial port device 
selection window will pop up, please FT8CN Quick Manual 4
Select the serial port used to control the radio (some radios have more than 1 serial port, 
please select another one of them).


## 第 5 页




## 第 6 页

How to import and export contact logs?
FT8CN contact log backup operation, or upload to a third-party platform (such as LotW or
QRZ) confirmation, and to import the log confirmed by the third-party platform back to 
FT8CN, you need to pass Access the background through the LAN to operate.
On the "Contact Records" page, click 
button, a background operation URL will pop up dialog box.
In the same LAN as FT8CN, in the browser
Enter the corresponding URL in , and you will enter the back of FT8CN
tower.
The import operation of FT8CN will intelligently identify the log is※
No duplicates, and the confirmation status of the intelligent update log.


## 第 7 页




## 第 8 页

How to quickly switch the frequency band of the radio station?
Click the button on the floating button on the FT8CN interface
The frequency selection column will pop up table, select the frequency you want to switch.


## 第 9 页

How to call a station in the message list?
On the "Decode" and "Call" interfaces, long press on the message list The message to be 
called will pop up a menu, select the call sign to call, FT8CN will automatically use this call
sign as the call target.
Make a quick call to the party that sent the message, and swipe left to correspond 
message, FT8CN will automatically call the sender of the message call, if slide within 2.5 
seconds from the beginning of this cycle, FT8CN The call will be initiated immediately, 
more than 2.5 seconds will be in the next call week period to start calling.


## 第 10 页

How can I see the waterfall chart while displaying the decoded message?
Switch the mobile phone to landscape mode, and you can see the waterfall on the 
"decode" interface and "call" interface Layout.


## 第 11 页

How to quickly set the sound frequency of emission?
In the state of different frequency transmission,
you can quickly set the transmission frequency by
clicking on different positions of the waterfall
diagram sound frequency.


## 第 12 页

Why both the FT8CN and the radio work normally, but the power 0 or very low?
FT8 communication adopts ssb mode, when the ALC is too low, the transmitting power of 
the radio will be
very low, even 0.
Adjust ALC, you can adjust the ALC value by adjusting the volume of the phone, if
Unable to adjust the volume (such as network control mode), you can click the floating 
button
Come Sets the strength of the sound signal.


## 第 13 页

Does FT8CN support free text?
The FT8CN can decode and emit free text. Free text messages for FT8 protocol up to 13 
characters mother. On the "Decoding" interface, long press 
will be in the "Standard Call" and "Free Text" modes.


## 第 14 页

Does FT8CN need time synchronization?
FT8 decoding requires time synchronization, if the time deviation is too large, it will not be 
able to decode, so The FT8CN also needs time synchronization. If your phone can access 
the Internet, you You don’t need to pay too much attention to the problem of time 
synchronization, FT8CN will automatically pass through the network when starting
Road synchronization time (the time of the mobile phone is not modified, but the offset 
value of the time is calculated).
You can also manually synchronize the time if you have internet access. In "Settings"
interface, click
button.
If internet access is not available, you can select an appropriate time in the time offset list
offset to correct the time.


## 第 15 页

The extended and detailed manual inChinese:
https://github.com/N0BOY/FT8CN/blob/cb1d1284c1ef962fa20f66c486aea4594054c34b/
FT8CN%E8%BD%AF%E4%BB%B6%E8%AE%BE%E8%AE%A1%E5%88%9D
%E8%A1%B7%E5%8F%8A%E4%BD%BF%E7%94%A8%E8%AF
%B4%E6%98%8E0.88%E7%89%88.pdf

