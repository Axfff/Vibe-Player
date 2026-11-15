# Privacy Policy

## Vibe-Player course fork: Discovery data handling (2026-11-03)

This section describes the Discovery increment in this repository. The upstream Music Player GO policy below is retained with its attribution and historical date; its statements about no Internet access, external links or stored data do not describe this new feature.

- Android Discovery uses the INTERNET permission to contact the service address selected by the user. Opening Discovery may check service/provider availability. Confirmed searches send music intent, optional scene/suggestions, selected catalog country and saved/hidden source identities to that service. The configured catalog receives the search terms and country; network services can observe connection metadata such as the connecting IP address.
- The verified manual configuration uses public iTunes metadata and original official links. Opening a result sends the user to the platform app/browser, whose account, region and data policies apply. No audio or artwork is downloaded by the Android Discovery client.
- Choosing or cancelling a single image does not upload it. Only an explicit positive upload-consent action with a configured live model permits the client to read, orient, resize, remove metadata and send that image through the selected service to the named model recipient. No whole-photo-library permission is requested. Refusal or unavailable analysis leaves manual input available.
- The app and supplied service do not persist original or processed photo files. Selection and suggestions are held in process memory. The server model request uses `store=false`; this is not a promise of zero provider retention. The account owner must review the actual model provider/account retention policy before enabling that route. Hosting/proxy logs also depend on the operator; the documented local demo disables HTTP access logs. No real model was authorized or called in the recorded Sprint 3 run.
- Discovery Collection is an app-private local JSON file excluded from Android automatic backup. It stores provider/native ID, official URL, title/artist snapshot, confirmed music intent, catalog country and hidden identities. It contains no photos, audio addresses, model key or local MediaStore track ID. Saved items can be removed; hidden identities can be cleared. Android app-data removal/uninstall clears the app's local Collection. The service address is separately retained in app preferences.
- The service temporarily holds query/session/returned-identity state in memory with bounded expiry; it does not create a user account. This increment adds no analytics SDK. Credentials remain in the operator's server configuration. Release transport requires HTTPS; the development APK permits HTTP only on explicitly named loopback hosts.

Implementation boundaries and current provider conditions are documented in [Android Discovery](docs/development/discovery/android-sprint3.md) and the [provider review](docs/development/discovery/provider-verification.md). Questions about this fork belong in [the course repository](https://github.com/Axfff/Vibe-Player/issues), not with the upstream author.

## Historical upstream policy


###### Effective date: 2018/07/30, Latest update: 2021/10/15


**Enrico D'Ortenzio** built the Music Player GO app as an Open Source app. This APP is provided at no cost and is intended for use as is.

This page is used to inform visitors regarding the developer policies with the collection, use, and disclosure of Personal Information if anyone decided to use the App.


This **privacy policy** document is to clearly state and provide information on what, how, and why personal data or information is collected, used and disclosed when this software is utilized, and the choices you have associated with that data. 

The objective of this policy document is to provide an open environment between the developer and users of this software by openly stating what information the developer collects or not.
By utilizing this software, it means you accept all the terms and conditions of the developer peculiar to the Music Player GO software.
This policy applies to this software and the developer would not be held responsible for the content or privacy practices on any third party app not operated by the developer to which this software links or that links to this software.
Therefore it is advisable to read up on the privacy policies of third party services that may be linked to this software.

<HR>

## Information Collection And Use

**Music Player GO does **NOT** collect or store any data.**

For utilization of Music-Player-GO, the software is designed to request the **READ** storage permission on the device on which the software's services is intended.

This permission has to be enabled so the software can read the storage and add music files to the music library; the information that may be collected with the enablement of this permission is stored locally on the device, and the developer has no access to that information. 
It can be revoked when it is not needed (this is privy to only devices running Android 6.0 and upward).
  
Other capabilities of the software are:

- Ability to prevent phone from sleeping while in use.
- Ability to modify audio settings to apply Equalizer settings.

<HR>

## Types of Data Collected

### Personal Data

The developer does **NOT** collect personally identifiable data while the software is being utilized.

<HR>

## Children's Privacy

The app does not address anyone under the age of 13. 
The developer does not knowingly collect personally identifiable information from children under 13.
If you are a parent or guardian and you are aware that your child has provided us with personal information by [email](mailto:enrico.dortenzio@outlook.com), please contact the team so that it will be able to take necessary actions.

This software is intended for general use, and has no restrictions on utilization by children.

Music-Player-GO **DOES NOT** contain any hazardous, violent, or any other content (e.g. augmented reality) that may require parental supervision.

<HR>

## Links To Other Sites

Music-Player-GO does not contain links to any other services.
All functionalities of the software are run independently and solely on the device.

<HR>

## Changes To This Privacy Policy

This privacy policy may be updated from time to time as the functionalities of the software are further developed and improved, and may likely contain disparities that will nullify this current policy.
Users will be notified of these changes prior to the change. The changes to this Privacy Policy are effective when they are made.

<HR>
       
## Disclaimer   

Music-Player-GO app is developed to be used for playing and listening to music stored on your device.
The developer will not be held accountable for how you decide to use this software and will not be liable for any losses and/or damages in connection with the use of the software.

<HR>

## Contact Us

This project would certainly improve with your contributions.
Feel free to propose contributions and suggestions to this project either by opening an issue or a pull requests.
For further inquiry regarding this privacy policy or any other issue, please contact the developer directly via [email](mailto:enrico.dortenzio@outlook.com) or by opening an issue on the app's official [GitHub repository](https://github.com/enricocid/Music-Player-GO/issues/new).
