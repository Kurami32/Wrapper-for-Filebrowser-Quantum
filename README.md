# Webview wrapper for Filebrowser Quantum
This Android app is a simple WebView wrapper for the [Filebrowser Quantum](https://github.com/gtsteffaniak/filebrowser) project, one of the best self-hosted web-based file managers that ever existed!

You can install [Filebrowser Quantum](https://github.com/gtsteffaniak/filebrowser) as a webapp within your browser as PWA (Progressive Web App) it works fine but is limited only to the home screen.

That's why I made this app (also because I'd like to have separate cookies and cache from my main browser). 

The app is just like any other app of your phone, it should appear in the app drawer of your launcher  letting you have a quick access -- also is searchable, allowing search-focused launchers find the app quickly.

> This is my first time doing an android app, and is also the first public project in GitHub, if there are issues or something, you can tell me, and will try to fix it :)

## Usage
> [!NOTE]
> You'll need to have a FileBrowser Quantum server running to use the app, if you are new to the Project, visit the [Filebrowser Quantum repository](https://github.com/gtsteffaniak/filebrowser) if you haven't already.

To use the app is very simple, just download the app from the [releases](https://github.com/Kurami32/Wrapper-for-Filebrowser-Quantum/releases) and install it on you desired device.

When you open the app for the first time you will have a screen like this:

| <img width="350" src="screenshots/example.jpg"> | 
|:---:| 

Just enter the URL of your FBQ server and click on the "save" button -- Don't forget that the URL must contain `http://` or `https://` at the begin :p

> [!IMPORTANT]
> Be cautious if you enter any other URL, or if you do a typo while setting the URL.
> If you do, just delete the app data from android settings to start again.
> The same applies if you want to change to other URL.

## Features
Some features that this app has over the PWA are:

- Is like any other app on your phone, you can search it with the launcher of your phone.
- Has a custom toast notification when uploading or downloading a file.
- Custom screen when there is no connection or the server is unreachable.
- Improved performance thanks to hardware acceleration.
- You can refresh the page using two (2) fingers swipe down gesture ⬇⬇
- You can also **delete cookies** using three (3) fingers swipe down gesture! ⬇⬇⬇

## Screenshots

| <img width="256" src="screenshots/setup-screen.jpg"> | <img width="256" src="screenshots/network-error-screen.jpg"> | <img width="256" src="screenshots/cookie-warning.jpg"> |
|:---:|:---:|:---:|
| Setup screen | Network error screen | Cookie deletion warning |
| <img width="256" src="screenshots/upload-toast-notification.jpg"> | <img width="256" src="screenshots/download-toast.jpg"> | <img width="256" src="screenshots/download-finished-toast.jpg"> |
| Upload toast notification | Download toast notification | Download/upload finished toast |

## Important notes
- How this is a WebView app, make sure that you always have up-to-date your webview component. I have tested this using the default [WebView](https://play.google.com/store/apps/details?id=com.google.android.webview) that comes in most devices, so, if you are using some custom webview I'm not sure if the app will work.
- This is just a wrapper, it just loads a specific URL provided -- In this case will load our filebrowser web page and render it in WebView :)
- If you find some issue, like a crash, the app not responding... Open an issue here and I'll try to fix it.
- If you find some issue non-related to the app, but instead you find something on the WebUI, go to the [FBQ repo](https://github.com/gtsteffaniak/filebrowser), and open an issue there. (But first test if is an issue related to my app by just trying to replicate in a normal browser)

## Know issues
The only issue is that you can't upload folders with the android filepicker, I don't know how to make it work to open the folder picker when pressing the folder button in the FBQ UI in the upload prompt.
If you know how to fix it a PR would be apprecited :D

## Contributions
You can contribute with GitHub issues if you find something not working, or~ you can also open a PR if you want to improve something.

## Licence
This repo uses the [MIT License](LICENSE), feel free to use anything of the code :)
