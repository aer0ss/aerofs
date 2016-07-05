// progress-monitor.cpp
// compile with: /D_UNICODE /DUNICODE /DWIN32 /D_WINDOWS /c

#include <windows.h>
#include <stdlib.h>
#include <string.h>
#include <tchar.h>
#include <CommCtrl.h>
#include <stdio.h>
#include "resource.h"

#define BUFSIZE 4096

// The main window class name and title bar text
static TCHAR szWindowClass[] = _T("AeroFS");

// Upgrade Message
static TCHAR upgradeMessage[] = _T("AeroFS is upgrading to the latest version. This may take a few minutes.");

// Icons
static HICON windowIcon;

// Window dimensions
static int const WINDOW_WIDTH = 400;
static int const WINDOW_HEIGHT = 130;
static int const PADDING = 20;
static int const ICON_SIZE = WINDOW_HEIGHT - 2.75 * PADDING;

// Forward declarations of functions
LRESULT CALLBACK WndProc(HWND, UINT, WPARAM, LPARAM);
DWORD WINAPI Listen(LPVOID progressBar);

//
//  FUNCTION: WinMain(HINSTANCE, HINSTANCE, LPSTR, int)
//
//  PURPOSE:  application entry point
//
int WINAPI WinMain(HINSTANCE hInstance,
    HINSTANCE hPrevInstance,
    LPSTR lpCmdLine,
    int nCmdShow)
{
    // Handle the command line arguments
    int possibleProgress = atoi(lpCmdLine);
    if (possibleProgress < 1)
    {
        ExitProcess(1);
    }

    windowIcon = (HICON)LoadImage(hInstance,
        MAKEINTRESOURCE(IDI_ICON1),
        1,
        ICON_SIZE,
        ICON_SIZE,
        LR_SHARED);

    WNDCLASSEX wcex;
    wcex.cbSize = sizeof(WNDCLASSEX);
    wcex.style = CS_HREDRAW | CS_VREDRAW | CS_NOCLOSE;
    wcex.lpfnWndProc = WndProc;
    wcex.cbClsExtra = 0;
    wcex.cbWndExtra = 0;
    wcex.hInstance = hInstance;
    wcex.hIcon = windowIcon;
    wcex.hCursor = LoadCursor(NULL, IDC_ARROW);
    wcex.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    wcex.lpszMenuName = NULL;
    wcex.lpszClassName = szWindowClass;
    wcex.hIconSm = windowIcon;

    if (!RegisterClassEx(&wcex))
    {
        return 1;
    }

    // Create the main window
    HWND hWnd = CreateWindow(
        szWindowClass,
        szWindowClass,
        WS_EX_TOOLWINDOW,
        CW_USEDEFAULT,
        CW_USEDEFAULT,
        WINDOW_WIDTH,
        WINDOW_HEIGHT,
        NULL,
        NULL,
        hInstance,
        NULL
        );

    // Create a progress bar window to add to the existing winddow
    InitCommonControls();
    HWND progressBar = CreateWindowEx(
        0,
        PROGRESS_CLASS,
        NULL,
        WS_CHILD | WS_VISIBLE | PBS_SMOOTH,
        2 * PADDING + ICON_SIZE,
        WINDOW_HEIGHT - 3.75 * PADDING,
        WINDOW_WIDTH - (4 * PADDING + ICON_SIZE),
        0.75 * PADDING,
        hWnd,
        NULL,
        hInstance,
        NULL);

    if (!hWnd || !progressBar)
    {
        return 1;
    }

    // Setup the progress bar
    SendMessage(progressBar,
        PBM_SETRANGE,
        0,
        MAKELPARAM(0, possibleProgress));

    // Show and paint the main window
    ShowWindow(hWnd,
        nCmdShow);
    UpdateWindow(hWnd);

    // Dispatch a thread for listening on stdin and updating progress
    HANDLE thread = CreateThread(
        NULL,
        0,
        Listen,
        progressBar,
        0,
        NULL);

    // Dispatch the main message loop
    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0))
    {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
    return (int)msg.wParam;
}

//
//  FUNCTION: WndProc(HWND, UINT, WPARAM, LPARAM)
//
//  PURPOSE:  Main window callback procedure
//
//  HANDLES:
//    WM_PAINT    - Paint the main window
//    WM_DESTROY  - post a quit message and return
//
LRESULT CALLBACK WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam)
{
    PAINTSTRUCT ps;
    HDC hdc;
    HFONT guiFont, oldFont;

    switch (message)
    {
    case WM_PAINT:
        // Grab the current drawing context
        hdc = BeginPaint(hWnd, &ps);

        // Recompute the upgrade message's position based on the window's size
        RECT upgradeMessageRect, iconRect;
        GetClientRect(hWnd, &iconRect);
        GetClientRect(hWnd, &upgradeMessageRect);
        upgradeMessageRect.left = upgradeMessageRect.left + 2 * PADDING + ICON_SIZE;
        upgradeMessageRect.top = upgradeMessageRect.top + 0.75 * PADDING;
        upgradeMessageRect.right = upgradeMessageRect.right - PADDING;

        // Set the font for this round of drawing
        guiFont = (HFONT)GetStockObject(DEFAULT_GUI_FONT);
        oldFont = (HFONT)SelectObject(hdc, guiFont);

        // Draw the upgrade message
        DrawTextEx(hdc,
            upgradeMessage,
            _tcslen(upgradeMessage),
            &upgradeMessageRect,
            DT_LEFT | DT_WORDBREAK | DT_NOCLIP,
            NULL);

        // Draw the icon
        DrawIconEx(hdc,
            PADDING,
            0.25 * PADDING,
            windowIcon,
            ICON_SIZE,
            ICON_SIZE,
            0,
            NULL,
            DI_NORMAL | DI_COMPAT);

        // Reset fonts
        SelectObject(hdc, oldFont);

        EndPaint(hWnd, &ps);
        break;
    case WM_DESTROY:
        PostQuitMessage(0);
        break;
    default:
        return DefWindowProc(hWnd, message, wParam, lParam);
        break;
    }

    return 0;
}

//
//  FUNCTION: Listen(LPVOID)
//
//  PURPOSE:  Listens to redirected stdin for updater progress and increments
//            a progress bar based on that progress
//
//  PARAMS:
//    LPVOID progressBar - a progress bar window to receive update messages
//
DWORD WINAPI Listen(LPVOID progressBar)
{
    HANDLE hStdin;
    BOOL readSuccess;
    DWORD dwRead;
    CHAR chBuf[BUFSIZE];

    int previousProgress = 0;
    int possibleProgress = SendMessage((HWND)progressBar,
        PBM_GETRANGE,
        FALSE,
        NULL);

    // By default, Win32 appliations have no stdin available. It becomes
    // available if you explicity create a console window for it or a parent
    // process redirects it. Therefore, getting stdin here will fail if you try
    // to run this application via a double-click or the the console
    hStdin = GetStdHandle(STD_INPUT_HANDLE);
    if (hStdin == INVALID_HANDLE_VALUE || possibleProgress == 0)
    {
        ExitProcess(1);
    }

    // While we are polling, it isn't awful, because ReadFile only returns
    // when there is something to return, when the handle is closed, or
    // in an error state. That way, we aren't hammering the UI with redraws
    for (;;)
    {
        readSuccess = ReadFile(hStdin, chBuf, BUFSIZE, &dwRead, NULL);
        if (!readSuccess)
        {
            ExitProcess(1);
        }
        else if (dwRead != 0)
        {
            int currentProgress = atoi(chBuf);
            if (currentProgress <= previousProgress || currentProgress == 0)
            {
                ExitProcess(1);
            }
            else if (currentProgress >= possibleProgress)
            {
                ExitProcess(0);
            }
            SendMessage((HWND)progressBar, PBM_SETPOS, currentProgress, 0);
        }
    }
    return dwRead;
}
