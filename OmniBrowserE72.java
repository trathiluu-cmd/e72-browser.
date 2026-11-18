import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.file.FileConnection;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

public class OmniBrowserE72 extends MIDlet implements CommandListener, Runnable {

    Display display;
    BrowserCanvas canvas;
    private TextBox inputBox;
    private Form settingsForm;
    private ChoiceGroup modeGroup;
    private ChoiceGroup searchEngineGroup;
    private ChoiceGroup uaGroup;
    private ChoiceGroup flagsGroup;
    private TextField customProxyField;
    private List bookmarkList;
    private List fileManagerList;
    private List suggestList;

    // 0 = Omnibox URL/Search, 1 = Find in page, 2 = Copy/Translate Viewer
    private int inputBoxMode = 0;

    // Commands
    private Command cmdOk, cmdCancel, cmdSuggest, cmdSaveSettings, cmdDeleteItem, cmdAddBm;
    private Command cmdMenuUrl, cmdMenuTranslate, cmdMenuDistiller, cmdMenuFind, cmdMenuDownloadLink;
    private Command cmdMenuCopy, cmdMenuAutoScroll, cmdMenuBm, cmdMenuFiles, cmdMenuTheme, cmdMenuFont;
    private Command cmdMenuMode, cmdMenuReload, cmdBack, cmdMenuExit;

    // Quản lý 3 Tab độc lập
    static final int TAB_COUNT = 3;
    TabDocument[] tabs = new TabDocument[TAB_COUNT];
    int activeTab = 0;

    // Trạng thái mạng & Thanh tiến trình
    String pendingUrl = "";
    String statusText = "OmniChromium v5.0 (TLS 1.3)";
    boolean isLoading = false;
    int loadProgress = 0;

    // Cấu hình kết nối & Tìm kiếm
    int connectionMode = 0;   // 0=Cloud Jina, 1=Search Bypass, 2=Direct TLS 1.3, 3=Private Worker
    int searchEngine = 0;     // 0=DuckDuckGo Lite, 1=Google Web Bypass, 2=Wikipedia VI
    int userAgentProfile = 0; // 0=Chrome 124, 1=Lynx Text, 2=Nokia E72 Native
    String customWorkerUrl = "";

    // Chromium Flags (Tính năng lõi Chromium)
    boolean flagDomDistiller = false; // Lọc rác Reader Mode (Phím X)
    boolean flagSaveData = true;      // Gửi header Save-Data: on

    // Quản lý Cookie phiên & Lịch sử toàn cục
    Hashtable cookieJar = new Hashtable();
    Vector globalHistory = new Vector();
    Vector suggestItems = new Vector();

    // Giao diện & Đọc sách
    int themeMode = 0;    // 0=Chromium Light, 1=Chromium Dark OLED, 2=Sepia Paper, 3=E-Ink
    int fontSizeMode = 0; // 0=Nhỏ, 1=Vừa, 2=Lớn
    boolean fullScreen = false;
    boolean autoScrolling = false;
    int autoScrollSpeed = 1200;

    // Tìm kiếm trong trang
    String lastFindKeyword = "";
    int highlightedLine = -1;

    // Dữ liệu Bookmarks & Offline RMS
    Vector bmTitles = new Vector();
    Vector bmUrls = new Vector();
    Vector offTitles = new Vector();
    Vector offContents = new Vector();
    Vector sdCardFiles = new Vector();

    private static final String RMS_CFG = "OmniChrCfg5";
    private static final String RMS_BM = "OmniChrBm5";
    private static final String RMS_OFF = "OmniChrOff5";

    public void startApp() {
        if (display == null) {
            display = Display.getDisplay(this);
            for (int i = 0; i < TAB_COUNT; i++) {
                tabs[i] = new TabDocument(i + 1);
            }

            loadSettings();
            loadBookmarks();
            loadOfflineVault();

            canvas = new BrowserCanvas(this);

            cmdOk = new Command("Đi tới", Command.OK, 1);
            cmdSuggest = new Command("Gợi ý Google (Omnibox)", Command.ITEM, 2);
            cmdCancel = new Command("Đóng", Command.CANCEL, 3);
            cmdSaveSettings = new Command("Lưu cấu hình", Command.OK, 1);
            cmdAddBm = new Command("+ Lưu trang hiện tại", Command.ITEM, 1);
            cmdDeleteItem = new Command("Xóa mục đang chọn", Command.ITEM, 2);

            cmdMenuUrl = new Command("1. Omnibox URL / Tìm kiếm (Phím U)", Command.ITEM, 1);
            cmdMenuTranslate = new Command("2. Google Dịch trang này (Phím T)", Command.ITEM, 2);
            cmdMenuDistiller = new Command("3. Bật/Tắt Chromium Reader Mode (Phím X)", Command.ITEM, 3);
            cmdMenuFind = new Command("4. Tìm chữ trong trang (Phím F)", Command.ITEM, 4);
            cmdMenuDownloadLink = new Command("5. Tải File từ Link đang chọn (Phím L)", Command.ITEM, 5);
            cmdMenuCopy = new Command("6. Sao chép văn bản (Phím C)", Command.ITEM, 6);
            cmdMenuAutoScroll = new Command("7. Tự động cuộn trang (Phím A)", Command.ITEM, 7);
            cmdMenuBm = new Command("8. Dấu trang - Bookmarks (Phím K)", Command.ITEM, 8);
            cmdMenuFiles = new Command("9. Quản lý File & Offline (Phím O)", Command.ITEM, 9);
            cmdMenuTheme = new Command("10. Đổi Giao diện 4 màu (Phím D)", Command.ITEM, 10);
            cmdMenuFont = new Command("11. Đổi Cỡ chữ (Phím Z)", Command.ITEM, 11);
            cmdMenuMode = new Command("12. Cấu hình Chromium Flags / TLS (Phím M)", Command.ITEM, 12);
            cmdMenuReload = new Command("13. Tải lại trang (Phím R)", Command.ITEM, 13);
            cmdBack = new Command("Quay lại (Phím B)", Command.BACK, 2);
            cmdMenuExit = new Command("Thoát", Command.EXIT, 99);

            canvas.addCommand(cmdMenuUrl);
            canvas.addCommand(cmdMenuTranslate);
            canvas.addCommand(cmdMenuDistiller);
            canvas.addCommand(cmdMenuFind);
            canvas.addCommand(cmdMenuDownloadLink);
            canvas.addCommand(cmdMenuCopy);
            canvas.addCommand(cmdMenuAutoScroll);
            canvas.addCommand(cmdMenuBm);
            canvas.addCommand(cmdMenuFiles);
            canvas.addCommand(cmdMenuTheme);
            canvas.addCommand(cmdMenuFont);
            canvas.addCommand(cmdMenuMode);
            canvas.addCommand(cmdMenuReload);
            canvas.addCommand(cmdBack);
            canvas.addCommand(cmdMenuExit);
            canvas.setCommandListener(this);

            for (int i = 0; i < TAB_COUNT; i++) {
                tabs[i].renderHomeSpeedDial();
            }
            display.setCurrent(canvas);
        }
    }

    public void pauseApp() {
        autoScrolling = false;
    }

    public void destroyApp(boolean unconditional) {
        autoScrolling = false;
        saveSettings();
    }

    TabDocument curTab() {
        return tabs[activeTab];
    }

    Font getBodyFont(boolean bold) {
        int size = Font.SIZE_SMALL;
        if (fontSizeMode == 1) size = Font.SIZE_MEDIUM;
        else if (fontSizeMode == 2) size = Font.SIZE_LARGE;
        return Font.getFont(Font.FACE_SYSTEM, bold ? Font.STYLE_BOLD : Font.STYLE_PLAIN, size);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdMenuExit) {
            destroyApp(true);
            notifyDestroyed();
        } else if (c == cmdMenuUrl) {
            openInputBox(0, curTab().url);
        } else if (c == cmdMenuTranslate) {
            translateCurrentViewport();
        } else if (c == cmdMenuDistiller) {
            toggleDomDistiller();
        } else if (c == cmdMenuFind) {
            openInputBox(1, lastFindKeyword);
        } else if (c == cmdMenuDownloadLink) {
            triggerSelectedLinkDownload();
        } else if (c == cmdMenuCopy) {
            openCopyBox("Sao chép Văn bản (Copy):", extractVisibleText(25));
        } else if (c == cmdMenuAutoScroll) {
            toggleAutoScroll();
        } else if (c == cmdMenuBm) {
            showBookmarkScreen();
        } else if (c == cmdMenuFiles) {
            showFileManagerScreen();
        } else if (c == cmdMenuTheme) {
            cycleTheme();
        } else if (c == cmdMenuFont) {
            cycleFontSize();
        } else if (c == cmdMenuReload) {
            navigateTo(curTab().url, false);
        } else if (c == cmdBack) {
            if (d != canvas) {
                display.setCurrent(canvas);
            } else {
                goBackInstant();
            }
        } else if (c == cmdMenuMode) {
            showSettings();
        } else if (c == cmdSuggest && d == inputBox) {
            String kw = inputBox.getString().trim();
            if (kw.length() > 0) {
                fetchGoogleSuggestions(kw);
            }
        } else if (c == cmdOk && d == inputBox) {
            String val = inputBox.getString().trim();
            display.setCurrent(canvas);
            if (val.length() > 0) {
                if (inputBoxMode == 0) {
                    processUrlOrSearch(val);
                } else if (inputBoxMode == 1) {
                    findInPage(val);
                }
            }
        } else if (c == cmdCancel) {
            display.setCurrent(canvas);
        } else if (c == cmdSaveSettings && d == settingsForm) {
            connectionMode = modeGroup.getSelectedIndex();
            searchEngine = searchEngineGroup.getSelectedIndex();
            userAgentProfile = uaGroup.getSelectedIndex();
            flagDomDistiller = flagsGroup.isSelected(0);
            flagSaveData = flagsGroup.isSelected(1);
            customWorkerUrl = customProxyField.getString().trim();
            saveSettings();
            statusText = "Đã lưu cấu hình Chromium!";
            display.setCurrent(canvas);
            canvas.repaint();
        } else if (d == suggestList && c == List.SELECT_COMMAND) {
            int idx = suggestList.getSelectedIndex();
            display.setCurrent(canvas);
            if (idx >= 0 && idx < suggestItems.size()) {
                String chosen = (String) suggestItems.elementAt(idx);
                processUrlOrSearch(chosen);
            }
        } else if (d == bookmarkList) {
            handleBookmarkAction(c);
        } else if (d == fileManagerList) {
            handleFileManagerAction(c);
        }
    }

    void openInputBox(int mode, String initText) {
        autoScrolling = false;
        inputBoxMode = mode;
        String title = (mode == 0) ? "Google Omnibox (Nhập URL / Từ khóa):" : "Tìm chữ trong trang:";
        inputBox = new TextBox(title, initText != null ? initText : "", 512, TextField.ANY);
        inputBox.addCommand(cmdOk);
        if (mode == 0) {
            inputBox.addCommand(cmdSuggest);
        }
        inputBox.addCommand(cmdCancel);
        inputBox.setCommandListener(this);
        display.setCurrent(inputBox);
    }

    private String extractVisibleText(int maxLines) {
        TabDocument t = curTab();
        StringBuffer sb = new StringBuffer();
        int end = Math.min(t.lines.size(), t.scrollLine + maxLines);
        for (int i = t.scrollLine; i < end; i++) {
            TabDocument.LineItem item = (TabDocument.LineItem) t.lines.elementAt(i);
            if (item.text.length() > 0) {
                sb.append(item.text).append("\n");
            }
        }
        String res = sb.toString();
        if (res.length() > 900) res = res.substring(0, 900);
        return res;
    }

    void openCopyBox(String title, String content) {
        autoScrolling = false;
        inputBoxMode = 2;
        if (content.length() > 1000) content = content.substring(0, 1000);
        inputBox = new TextBox(title, content, 1024, TextField.ANY);
        inputBox.addCommand(cmdCancel);
        inputBox.setCommandListener(this);
        display.setCurrent(inputBox);
    }

    // Tính năng Google Omnibox Autocomplete (suggestqueries.google.com)
    private void fetchGoogleSuggestions(final String keyword) {
        display.setCurrent(canvas);
        statusText = "Đang lấy gợi ý Google Omnibox...";
        canvas.repaint();

        Thread sugThread = new Thread(new Runnable() {
            public void run() {
                try {
                    String url = "https://suggestqueries.google.com/complete/search?client=toolbar&hl=vi&gl=VN&q="
                                 + NetRouter.urlEncode(keyword);
                    String xml = HttpEngine.fetchSmallString(OmniBrowserE72.this, url);
                    suggestItems.removeAllElements();
                    suggestItems.addElement(keyword);

                    int idx = 0;
                    while (idx < xml.length() && suggestItems.size() < 10) {
                        int pos = xml.indexOf("data=\"", idx);
                        if (pos == -1) break;
                        int end = xml.indexOf("\"", pos + 6);
                        if (end == -1) break;
                        String item = xml.substring(pos + 6, end).trim();
                        if (item.length() > 0 && !item.equals(keyword)) {
                            suggestItems.addElement(item);
                        }
                        idx = end + 1;
                    }

                    suggestList = new List("Gợi ý từ Google Omnibox:", List.IMPLICIT);
                    for (int i = 0; i < suggestItems.size(); i++) {
                        suggestList.append((i + 1) + ". " + (String) suggestItems.elementAt(i), null);
                    }
                    suggestList.addCommand(cmdCancel);
                    suggestList.setCommandListener(OmniBrowserE72.this);
                    statusText = "Đã tải " + suggestItems.size() + " gợi ý từ Google";
                    display.setCurrent(suggestList);
                } catch (Exception e) {
                    statusText = "Lỗi gợi ý Google: " + e.getMessage();
                    canvas.repaint();
                }
            }
        });
        sugThread.start();
    }

    // Tính năng Google Translate GTX (Dịch trực tiếp đoạn văn bản đang đọc sang Tiếng Việt)
    void translateCurrentViewport() {
        if (isLoading) return;
        final String sourceText = extractVisibleText(16);
        if (sourceText.trim().length() == 0) {
            statusText = "Không có văn bản để dịch";
            canvas.repaint();
            return;
        }

        isLoading = true;
        loadProgress = 30;
        statusText = "Đang dịch qua Google Translate TLS 1.3...";
        canvas.repaint();

        Thread transThread = new Thread(new Runnable() {
            public void run() {
                try {
                    String q = sourceText;
                    if (q.length() > 500) q = q.substring(0, 500);
                    String api = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=vi&dt=t&q="
                                 + NetRouter.urlEncode(q);
                    String json = HttpEngine.fetchSmallString(OmniBrowserE72.this, api);
                    String translated = GoogleParser.parseTranslateResponse(json);

                    TabDocument t = curTab();
                    t.lines.insertElementAt(new TabDocument.LineItem("=== BẢN DỊCH GOOGLE TRANSLATE ===", -1, true), t.scrollLine);
                    Font f = getBodyFont(false);
                    int maxW = canvas.getWidth() - 10;
                    Vector wrapped = t.wrapStandaloneText(translated, f, maxW > 200 ? maxW : 310);
                    for (int i = 0; i < wrapped.size(); i++) {
                        t.lines.insertElementAt(
                            new TabDocument.LineItem((String) wrapped.elementAt(i), -1, false),
                            t.scrollLine + 1 + i
                        );
                    }
                    t.lines.insertElementAt(
                        new TabDocument.LineItem("=================================", -1, true),
                        t.scrollLine + 1 + wrapped.size()
                    );
                    t.rebuildMap();
                    statusText = "Đã chèn bản dịch Tiếng Việt tại chỗ!";
                } catch (Exception e) {
                    statusText = "Lỗi Google Dịch: " + e.getMessage();
                } finally {
                    isLoading = false;
                    loadProgress = 0;
                    canvas.repaint();
                }
            }
        });
        transThread.start();
    }

    // Bật/Tắt thuật toán lọc rác Chromium DOM Distiller (Phím X)
    void toggleDomDistiller() {
        flagDomDistiller = !flagDomDistiller;
        saveSettings();
        TabDocument t = curTab();
        if (t.fullRawCache != null && t.fullRawCache.length() > 0) {
            int maxW = canvas.getWidth() - 10;
            t.parseAndWrapChunk(getBodyFont(false), maxW > 200 ? maxW : 310, flagDomDistiller);
            t.scrollLine = 0;
        }
        statusText = "Chromium DOM Distiller: " + (flagDomDistiller ? "BẬT (Đã lọc menu rác)" : "TẮT");
        canvas.repaint();
    }

    void toggleAutoScroll() {
        autoScrolling = !autoScrolling;
        if (autoScrolling) {
            statusText = "Tự cuộn: Bật (Bấm phím bất kỳ để dừng)";
            Thread scroller = new Thread(new Runnable() {
                public void run() {
                    while (autoScrolling) {
                        try {
                            Thread.sleep(autoScrollSpeed);
                        } catch (Exception e) {}
                        if (!autoScrolling) break;
                        TabDocument t = curTab();
                        if (t.scrollLine + 1 < t.lines.size()) {
                            t.scrollLine++;
                            canvas.repaint();
                        } else {
                            autoScrolling = false;
                            statusText = "Đã cuộn tới cuối phần này";
                            canvas.repaint();
                            break;
                        }
                    }
                }
            });
            scroller.start();
        } else {
            statusText = "Đã dừng Tự động cuộn";
        }
        canvas.repaint();
    }

    void triggerSelectedLinkDownload() {
        TabDocument t = curTab();
        if (t.selectedLink >= 0 && t.selectedLink < t.links.size()) {
            String targetUrl = (String) t.links.elementAt(t.selectedLink);
            StorageEngine.downloadBinaryFile(this, targetUrl);
        } else {
            statusText = "Chưa có Link nào được chọn để tải";
            canvas.repaint();
        }
    }

    void cycleTheme() {
        themeMode = (themeMode + 1) % 4;
        saveSettings();
        String[] names = {"Chromium Light", "Chromium Dark OLED", "Giấy cổ Sepia", "E-Ink Đen Trắng"};
        statusText = "Giao diện: " + names[themeMode];
        canvas.repaint();
    }

    void cycleFontSize() {
        fontSizeMode = (fontSizeMode + 1) % 3;
        saveSettings();
        TabDocument t = curTab();
        if (t.fullRawCache != null && t.fullRawCache.length() > 0) {
            int maxW = canvas.getWidth() - 10;
            t.parseAndWrapChunk(getBodyFont(false), maxW > 200 ? maxW : 310, flagDomDistiller);
        }
        String[] fNames = {"Nhỏ (Small)", "Vừa (Medium)", "Lớn (Large)"};
        statusText = "Cỡ chữ: " + fNames[fontSizeMode];
        canvas.repaint();
    }

    void changeChunkPage(int delta) {
        TabDocument t = curTab();
        if (t.totalChunks <= 1) {
            statusText = "Trang này chỉ có 1 phần (1/1)";
            canvas.repaint();
            return;
        }
        int nextChunk = t.currentChunk + delta;
        if (nextChunk < 0 || nextChunk >= t.totalChunks) {
            statusText = "Đã ở phần giới hạn (" + (t.currentChunk + 1) + "/" + t.totalChunks + ")";
            canvas.repaint();
            return;
        }
        t.currentChunk = nextChunk;
        int maxW = canvas.getWidth() - 10;
        t.parseAndWrapChunk(getBodyFont(false), maxW > 200 ? maxW : 310, flagDomDistiller);
        t.scrollLine = 0;
        statusText = "Đang xem Phần " + (t.currentChunk + 1) + "/" + t.totalChunks;
        canvas.repaint();
    }

    void findInPage(String keyword) {
        lastFindKeyword = keyword;
        String kwLower = keyword.toLowerCase();
        TabDocument t = curTab();
        int total = t.lines.size();
        if (total == 0) return;

        int start = (highlightedLine >= 0) ? (highlightedLine + 1) : t.scrollLine;
        for (int step = 0; step < total; step++) {
            int idx = (start + step) % total;
            TabDocument.LineItem item = (TabDocument.LineItem) t.lines.elementAt(idx);
            if (item.text.toLowerCase().indexOf(kwLower) != -1) {
                highlightedLine = idx;
                t.scrollLine = Math.max(0, idx - 2);
                if (item.linkIndex >= 0) {
                    t.selectedLink = item.linkIndex;
                }
                statusText = "Tìm thấy dòng " + (idx + 1) + ": " + keyword;
                canvas.repaint();
                return;
            }
        }
        highlightedLine = -1;
        statusText = "Không tìm thấy: " + keyword;
        canvas.repaint();
    }

    void showBookmarkScreen() {
        autoScrolling = false;
        bookmarkList = new List("Dấu trang (Chromium Bookmarks)", List.IMPLICIT);
        for (int i = 0; i < bmTitles.size(); i++) {
            bookmarkList.append((i + 1) + ". " + (String) bmTitles.elementAt(i), null);
        }
        bookmarkList.addCommand(cmdAddBm);
        if (bmTitles.size() > 0) {
            bookmarkList.addCommand(cmdDeleteItem);
        }
        bookmarkList.addCommand(cmdBack);
        bookmarkList.setCommandListener(this);
        display.setCurrent(bookmarkList);
    }

    private void handleBookmarkAction(Command c) {
        if (c == cmdAddBm) {
            TabDocument t = curTab();
            String title = NetRouter.getHost(t.url) + " - " + t.url;
            if (title.length() > 45) title = title.substring(0, 45);
            bmTitles.addElement(title);
            bmUrls.addElement(t.url);
            saveBookmarks();
            statusText = "Đã lưu Dấu trang!";
            display.setCurrent(canvas);
            canvas.repaint();
        } else if (c == cmdDeleteItem) {
            int idx = bookmarkList.getSelectedIndex();
            if (idx >= 0 && idx < bmTitles.size()) {
                bmTitles.removeElementAt(idx);
                bmUrls.removeElementAt(idx);
                saveBookmarks();
                showBookmarkScreen();
            }
        } else if (c == List.SELECT_COMMAND) {
            int idx = bookmarkList.getSelectedIndex();
            if (idx >= 0 && idx < bmUrls.size()) {
                String target = (String) bmUrls.elementAt(idx);
                display.setCurrent(canvas);
                navigateTo(target, true);
            }
        }
    }

    void showFileManagerScreen() {
        autoScrolling = false;
        fileManagerList = new List("Quản lý File & Kho Offline", List.IMPLICIT);
        fileManagerList.append("[1] Lưu trang vào Kho Offline (Trong App)", null);
        fileManagerList.append("[2] Xuất trang ra thẻ nhớ E:/OmniE72/*.txt", null);
        fileManagerList.append("[3] Xuất mã nguồn ra E:/OmniE72/*.html", null);
        fileManagerList.append("[4] Quét & Đọc file từ E:/OmniE72/", null);

        for (int i = 0; i < offTitles.size(); i++) {
            fileManagerList.append("[App] " + (String) offTitles.elementAt(i), null);
        }
        for (int i = 0; i < sdCardFiles.size(); i++) {
            fileManagerList.append("[E:/] " + (String) sdCardFiles.elementAt(i), null);
        }

        if (offTitles.size() > 0) {
            fileManagerList.addCommand(cmdDeleteItem);
        }
        fileManagerList.addCommand(cmdBack);
        fileManagerList.setCommandListener(this);
        display.setCurrent(fileManagerList);
    }

    private void handleFileManagerAction(Command c) {
        int idx = fileManagerList.getSelectedIndex();
        if (c == cmdDeleteItem) {
            int realIdx = idx - 4;
            if (realIdx >= 0 && realIdx < offTitles.size()) {
                offTitles.removeElementAt(realIdx);
                offContents.removeElementAt(realIdx);
                saveOfflineVault();
                showFileManagerScreen();
            }
            return;
        }
        if (c == List.SELECT_COMMAND) {
            TabDocument t = curTab();
            if (idx == 0) {
                String dump = t.buildPlainTextDump();
                String label = NetRouter.getHost(t.url) + " (" + (dump.length() / 1024) + "KB)";
                offTitles.addElement(label);
                offContents.addElement(dump);
                saveOfflineVault();
                statusText = "Đã lưu vào Kho Offline nội bộ!";
                display.setCurrent(canvas);
                canvas.repaint();
            } else if (idx == 1) {
                display.setCurrent(canvas);
                StorageEngine.exportTabToFile(this, t, false);
            } else if (idx == 2) {
                display.setCurrent(canvas);
                StorageEngine.exportTabToFile(this, t, true);
            } else if (idx == 3) {
                StorageEngine.scanSdCardFolder(this);
            } else {
                int appOffset = idx - 4;
                if (appOffset >= 0 && appOffset < offTitles.size()) {
                    String content = (String) offContents.elementAt(appOffset);
                    loadRawDocumentIntoCurrentTab("offline://rms", content, "Offline: " + (String) offTitles.elementAt(appOffset));
                    display.setCurrent(canvas);
                } else {
                    int sdOffset = appOffset - offTitles.size();
                    if (sdOffset >= 0 && sdOffset < sdCardFiles.size()) {
                        String fName = (String) sdCardFiles.elementAt(sdOffset);
                        display.setCurrent(canvas);
                        StorageEngine.readSdCardFile(this, fName);
                    }
                }
            }
        }
    }

    void loadRawDocumentIntoCurrentTab(String sourceUrl, String rawText, String statusMsg) {
        TabDocument t = curTab();
        t.url = sourceUrl;
        int maxW = canvas.getWidth() - 10;
        t.loadDocument(rawText, sourceUrl, getBodyFont(false), maxW > 200 ? maxW : 310, flagDomDistiller);
        t.scrollLine = 0;
        t.selectedLink = t.links.size() > 0 ? 0 : -1;
        statusText = statusMsg;
        canvas.repaint();
    }

    void showSettings() {
        autoScrolling = false;
        settingsForm = new Form("Cấu hình Chromium & TLS 1.3");
        String[] modes = {
            "1. Cloud Reader (Jina TLS 1.3 - Nén mạnh)",
            "2. Search & Web Bypass (Vượt chặn Google)",
            "3. Direct TLS 1.3 (Kết nối thẳng + Cookie)",
            "4. Private Worker (Máy chủ riêng)"
        };
        modeGroup = new ChoiceGroup("Chế độ Kết nối:", Choice.EXCLUSIVE, modes, null);
        modeGroup.setSelectedIndex(connectionMode, true);

        String[] engines = {
            "1. DuckDuckGo HTML (Nhanh & Không Captcha)",
            "2. Google Web Bypass (Qua Cloud Proxy)",
            "3. Wikipedia Tiếng Việt"
        };
        searchEngineGroup = new ChoiceGroup("Công cụ Tìm kiếm:", Choice.EXCLUSIVE, engines, null);
        searchEngineGroup.setSelectedIndex(searchEngine, true);

        String[] uas = {
            "1. Chromium 124 Desktop (Mặc định)",
            "2. Lynx Text Browser (Vượt tường JS)",
            "3. Nokia E72 Symbian S60v3"
        };
        uaGroup = new ChoiceGroup("Giả lập User-Agent:", Choice.EXCLUSIVE, uas, null);
        uaGroup.setSelectedIndex(userAgentProfile, true);

        String[] flagLabels = {
            "Chromium DOM Distiller (Lọc sạch Menu rác)",
            "Chromium Data Saver (Header Save-Data: on)"
        };
        flagsGroup = new ChoiceGroup("Chromium Flags:", Choice.MULTIPLE, flagLabels, null);
        flagsGroup.setSelectedIndex(0, flagDomDistiller);
        flagsGroup.setSelectedIndex(1, flagSaveData);

        customProxyField = new TextField("URL Worker riêng (nếu chọn 4):", customWorkerUrl, 256, TextField.URL);

        settingsForm.append(modeGroup);
        settingsForm.append(searchEngineGroup);
        settingsForm.append(uaGroup);
        settingsForm.append(flagsGroup);
        settingsForm.append(customProxyField);
        settingsForm.addCommand(cmdSaveSettings);
        settingsForm.addCommand(cmdCancel);
        settingsForm.setCommandListener(this);
        display.setCurrent(settingsForm);
    }

    void processUrlOrSearch(String input) {
        if (input.startsWith("chrome://")) {
            renderChromeInternalPage(input);
            return;
        }
        String target = NetRouter.buildNavigationTarget(input, connectionMode, searchEngine, customWorkerUrl);
        navigateTo(target, true);
    }

    // Xử lý các trang nội bộ chuẩn Chromium (chrome://version, chrome://history, chrome://flags)
    private void renderChromeInternalPage(String chromeUrl) {
        TabDocument t = curTab();
        t.url = chromeUrl;
        t.lines.removeAllElements();
        t.links.removeAllElements();

        long freeMem = Runtime.getRuntime().freeMemory() / 1024;
        long totalMem = Runtime.getRuntime().totalMemory() / 1024;

        if (chromeUrl.indexOf("history") != -1) {
            t.addLine("CHROME://HISTORY - LỊCH SỬ DUYỆT WEB", -1, true);
            if (globalHistory.size() == 0) {
                t.addLine("Chưa có lịch sử truy cập trong phiên này.", -1, false);
            } else {
                for (int i = globalHistory.size() - 1; i >= 0; i--) {
                    String hUrl = (String) globalHistory.elementAt(i);
                    t.addLink(hUrl, hUrl);
                }
            }
        } else {
            t.addLine("OMNICHROMIUM E72 v5.0 (CHROME://VERSION)", -1, true);
            t.addLine("• Nhân xử lý: Chromium Micro-DOM + DOM Distiller", -1, false);
            t.addLine("• Bảo mật mạng: Nokia E72 Native TLS 1.3", -1, false);
            t.addLine("• Bộ nhớ Java Heap: Trống " + freeMem + " KB / Tổng " + totalMem + " KB", -1, false);
            t.addLine("• Số miền đang giữ Cookie: " + cookieJar.size(), -1, false);
            t.addLine("• Chế độ DOM Distiller (Phím X): " + (flagDomDistiller ? "BẬT" : "TẮT"), -1, false);
            t.addLine("• Header Save-Data: " + (flagSaveData ? "BẬT" : "TẮT"), -1, false);
            t.addLine("--------------------------------------------", -1, false);
            t.addLink("Xem Lịch sử duyệt web (chrome://history)", "chrome://history");
            t.addLink("Quay về Trang chủ Speed Dial", "chrome://newtab");
        }

        if (chromeUrl.indexOf("newtab") != -1) {
            t.renderHomeSpeedDial();
        } else {
            t.rebuildMap();
            t.scrollLine = 0;
            t.selectedLink = t.links.size() > 0 ? 0 : -1;
        }
        statusText = chromeUrl;
        canvas.repaint();
    }

    void goBackInstant() {
        autoScrolling = false;
        TabDocument t = curTab();
        if (t.history.size() > 0) {
            TabDocument.HistoryEntry entry = (TabDocument.HistoryEntry) t.history.lastElement();
            t.history.removeElementAt(t.history.size() - 1);

            if (entry.cachedRaw != null && entry.cachedRaw.length() > 0) {
                t.url = entry.url;
                t.fullRawCache = entry.cachedRaw;
                t.currentChunk = entry.chunkIndex;
                int maxW = canvas.getWidth() - 10;
                t.recalculateChunks();
                t.parseAndWrapChunk(getBodyFont(false), maxW > 200 ? maxW : 310, flagDomDistiller);
                t.scrollLine = entry.scrollLine;
                t.selectedLink = entry.selectedLink;
                statusText = "Đã lùi tức thì: " + NetRouter.getHost(t.url);
                canvas.repaint();
            } else {
                navigateTo(entry.url, false);
            }
        } else {
            t.renderHomeSpeedDial();
            statusText = "Trang chủ Tab " + t.tabId;
            canvas.repaint();
        }
    }

    void switchTab(int dir) {
        autoScrolling = false;
        activeTab = (activeTab + dir + TAB_COUNT) % TAB_COUNT;
        highlightedLine = -1;
        TabDocument t = curTab();
        statusText = "Tab " + (activeTab + 1) + "/3 [" + (t.currentChunk + 1) + "/" + t.totalChunks + "] " + NetRouter.getHost(t.url);
        canvas.repaint();
    }

    void navigateTo(String url, boolean pushHistory) {
        if (isLoading || url == null || url.length() == 0) return;
        autoScrolling = false;

        if (url.startsWith("chrome://")) {
            renderChromeInternalPage(url);
            return;
        }

        if (NetRouter.isBinaryDownloadUrl(url)) {
            StorageEngine.downloadBinaryFile(this, url);
            return;
        }

        TabDocument t = curTab();
        if (pushHistory && t.url != null && t.url.length() > 0 && !t.url.equals(url)) {
            if (t.history.size() >= 6) {
                t.history.removeElementAt(0);
            }
            t.history.addElement(new TabDocument.HistoryEntry(
                t.url, t.fullRawCache, t.scrollLine, t.selectedLink, t.currentChunk
            ));
        }
        if (globalHistory.size() > 30) {
            globalHistory.removeElementAt(0);
        }
        globalHistory.addElement(url);

        pendingUrl = url;
        highlightedLine = -1;
        Thread netThread = new Thread(this);
        netThread.start();
    }

    public void run() {
        isLoading = true;
        loadProgress = 10;
        TabDocument targetTab = curTab();
        statusText = "Đang kết nối TLS 1.3...";
        canvas.repaint();

        try {
            String fetchUrl = NetRouter.buildFetchUrl(pendingUrl, connectionMode, customWorkerUrl);
            String rawText = HttpEngine.fetchDocument(this, fetchUrl, connectionMode, userAgentProfile);

            loadProgress = 85;
            statusText = flagDomDistiller ? "Đang lọc qua Chromium Distiller..." : "Đang dàn trang Micro-DOM...";
            canvas.repaint();

            int maxW = canvas.getWidth() - 10;
            targetTab.loadDocument(rawText, pendingUrl, getBodyFont(false), maxW > 200 ? maxW : 310, flagDomDistiller);
            rawText = null;
            System.gc();

            targetTab.scrollLine = 0;
            targetTab.selectedLink = targetTab.links.size() > 0 ? 0 : -1;
            loadProgress = 100;

            String partInfo = (targetTab.totalChunks > 1) ? " [Phần 1/" + targetTab.totalChunks + "]" : "";
            String chapInfo = (targetTab.nextChapterUrl != null) ? " [N:ChươngTiếp]" : "";
            statusText = NetRouter.getHost(targetTab.url) + partInfo + chapInfo;

        } catch (Exception e) {
            targetTab.lines.removeAllElements();
            targetTab.links.removeAllElements();
            targetTab.addLine("LỖI KẾT NỐI: " + e.toString(), -1, true);
            targetTab.addLine("URL: " + pendingUrl, -1, false);
            targetTab.addLine("Mẹo: Bấm phím [M] chuyển sang Chế độ 1 (Cloud Reader) hoặc kiểm tra mạng.", -1, false);
            targetTab.rebuildMap();
            statusText = "Lỗi tải trang";
        } finally {
            isLoading = false;
            loadProgress = 0;
            canvas.repaint();
        }
    }

    private void loadSettings() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RMS_CFG, true);
            if (rs.getNumRecords() >= 1) {
                byte[] data = rs.getRecord(1);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                connectionMode = dis.readInt();
                searchEngine = dis.readInt();
                userAgentProfile = dis.readInt();
                customWorkerUrl = dis.readUTF();
                themeMode = dis.readInt();
                fontSizeMode = dis.readInt();
                flagDomDistiller = dis.readBoolean();
                flagSaveData = dis.readBoolean();
                dis.close();
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception e) {}
            }
        }
    }

    private void saveSettings() {
        RecordStore rs = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(connectionMode);
            dos.writeInt(searchEngine);
            dos.writeInt(userAgentProfile);
            dos.writeUTF(customWorkerUrl != null ? customWorkerUrl : "");
            dos.writeInt(themeMode);
            dos.writeInt(fontSizeMode);
            dos.writeBoolean(flagDomDistiller);
            dos.writeBoolean(flagSaveData);
            dos.close();
            byte[] data = baos.toByteArray();

            rs = RecordStore.openRecordStore(RMS_CFG, true);
            if (rs.getNumRecords() == 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(1, data, 0, data.length);
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception e) {}
            }
        }
    }

    private void loadBookmarks() {
        bmTitles.removeAllElements();
        bmUrls.removeAllElements();
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RMS_BM, true);
            if (rs.getNumRecords() >= 1) {
                byte[] data = rs.getRecord(1);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                int count = dis.readInt();
                for (int i = 0; i < count; i++) {
                    bmTitles.addElement(dis.readUTF());
                    bmUrls.addElement(dis.readUTF());
                }
                dis.close();
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception e) {}
            }
        }
    }

    private void saveBookmarks() {
        RecordStore rs = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            int count = bmTitles.size();
            dos.writeInt(count);
            for (int i = 0; i < count; i++) {
                dos.writeUTF((String) bmTitles.elementAt(i));
                dos.writeUTF((String) bmUrls.elementAt(i));
            }
            dos.close();
            byte[] data = baos.toByteArray();

            rs = RecordStore.openRecordStore(RMS_BM, true);
            if (rs.getNumRecords() == 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(1, data, 0, data.length);
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception e) {}
            }
        }
    }

    private void loadOfflineVault() {
        offTitles.removeAllElements();
        offContents.removeAllElements();
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RMS_OFF, true);
            if (rs.getNumRecords() >= 1) {
                byte[] data = rs.getRecord(1);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                int count = dis.readInt();
                for (int i = 0; i < count; i++) {
                    offTitles.addElement(dis.readUTF());
                    offContents.addElement(dis.readUTF());
                }
                dis.close();
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception e) {}
            }
        }
    }

    private void saveOfflineVault() {
        RecordStore rs = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            int count = offTitles.size();
            dos.writeInt(count);
            for (int i = 0; i < count; i++) {
                dos.writeUTF((String) offTitles.elementAt(i));
                dos.writeUTF((String) offContents.elementAt(i));
            }
            dos.close();
            byte[] data = baos.toByteArray();

            rs = RecordStore.openRecordStore(RMS_OFF, true);
            if (rs.getNumRecords() == 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(1, data, 0, data.length);
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception e) {}
            }
        }
    }

    // Giao diện Canvas phong cách Chromium Material
    static class BrowserCanvas extends Canvas {
        private final OmniBrowserE72 app;
        private final Font headerFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);

        BrowserCanvas(OmniBrowserE72 b) {
            this.app = b;
        }

        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            TabDocument tab = app.curTab();
            Font plainFont = app.getBodyFont(false);
            Font boldFont = app.getBodyFont(true);

            int bgColor = 0xFFFFFF;
            int textColor = 0x202124;
            int headingColor = 0x1A73E8;
            int linkColor = 0x1967D2;
            int selLinkBg = 0x1A73E8;
            int selLinkFg = 0xFFFFFF;
            int headerBg = 0x1A73E8; // Xanh Google Chrome

            if (app.themeMode == 1) {
                bgColor = 0x121212;
                textColor = 0xE8EAED;
                headingColor = 0x8AB4F8;
                linkColor = 0x8AB4F8;
                selLinkBg = 0x3C4043;
                selLinkFg = 0x8AB4F8;
                headerBg = 0x202124;
            } else if (app.themeMode == 2) {
                bgColor = 0xF4ECD8;
                textColor = 0x3B2F2F;
                headingColor = 0x6E1E00;
                linkColor = 0x8B2500;
                selLinkBg = 0x704214;
                selLinkFg = 0xFFF8DC;
                headerBg = 0x7B1F1F;
            } else if (app.themeMode == 3) {
                bgColor = 0xFFFFFF;
                textColor = 0x000000;
                headingColor = 0x000000;
                linkColor = 0x000000;
                selLinkBg = 0x000000;
                selLinkFg = 0xFFFFFF;
                headerBg = 0x000000;
            }

            g.setColor(bgColor);
            g.fillRect(0, 0, w, h);

            int headerH = headerFont.getHeight() + 6;
            g.setColor(headerBg);
            g.fillRect(0, 0, w, headerH);
            g.setColor(0xFFFFFF);
            g.setFont(headerFont);

            String distillerBadge = app.flagDomDistiller ? "[R]" : "";
            String chunkTag = (tab.totalChunks > 1) ? "(" + (tab.currentChunk + 1) + "/" + tab.totalChunks + ")" : "";
            String tabBar = "[T" + (app.activeTab + 1) + chunkTag + "]" + distillerBadge + " ";
            g.drawString(tabBar + app.statusText, 3, 3, Graphics.TOP | Graphics.LEFT);

            if (app.isLoading && app.loadProgress > 0) {
                g.setColor(0x34A853); // Xanh lá Google
                int pw = (w * app.loadProgress) / 100;
                g.fillRect(0, headerH - 2, pw, 2);
            }

            int footerH = app.fullScreen ? 0 : (headerFont.getHeight() + 4);
            int contentY = headerH + 2;
            int contentH = h - headerH - footerH - 4;
            int lineH = plainFont.getHeight() + 2;
            int maxVisibleLines = contentH / lineH;
            if (maxVisibleLines < 1) maxVisibleLines = 1;

            int totalLines = tab.lines.size();

            for (int i = 0; i < maxVisibleLines; i++) {
                int idx = tab.scrollLine + i;
                if (idx >= totalLines) break;

                TabDocument.LineItem item = (TabDocument.LineItem) tab.lines.elementAt(idx);
                int y = contentY + (i * lineH);

                g.setFont(item.isBold ? boldFont : plainFont);

                if (idx == app.highlightedLine) {
                    g.setColor(0xFBBC05); // Vàng Google Highlight
                    g.fillRect(2, y, w - 8, lineH);
                    g.setColor(0x000000);
                } else if (item.linkIndex >= 0) {
                    if (item.linkIndex == tab.selectedLink) {
                        g.setColor(selLinkBg);
                        g.fillRect(2, y, w - 8, lineH);
                        g.setColor(selLinkFg);
                    } else {
                        g.setColor(linkColor);
                    }
                } else if (item.isBold) {
                    g.setColor(headingColor);
                } else {
                    g.setColor(textColor);
                }
                g.drawString(item.text, 4, y + 1, Graphics.TOP | Graphics.LEFT);
            }

            if (totalLines > maxVisibleLines) {
                g.setColor(app.themeMode == 1 ? 0x333333 : 0xDDDDDD);
                g.fillRect(w - 4, contentY, 3, contentH);
                int barH = (contentH * maxVisibleLines) / totalLines;
                if (barH < 8) barH = 8;
                int barY = contentY + ((contentH - barH) * tab.scrollLine) / (totalLines - maxVisibleLines);
                g.setColor(0xEA4335); // Đỏ Google
                g.fillRect(w - 4, barY, 3, barH);
            }

            if (!app.fullScreen) {
                g.setColor(0x202124);
                g.fillRect(0, h - footerH, w, footerH);
                g.setColor(0xE8EAED);
                g.setFont(headerFont);
                String navInfo = (tab.nextChapterUrl != null)
                    ? "U:Omnibox | T:Dịch | X:LọcRác | N/P:Chương"
                    : "U:Omnibox | T:Dịch | X:LọcRác | W/S:Link | B:Lùi";
                g.drawString(navInfo, 4, h - footerH + 2, Graphics.TOP | Graphics.LEFT);
            }
        }

        protected void keyPressed(int keyCode) {
            if (app.autoScrolling) {
                app.autoScrolling = false;
                app.statusText = "Đã dừng Tự động cuộn";
                repaint();
                return;
            }

            TabDocument tab = app.curTab();

            if (keyCode == -8 || keyCode == 8 || keyCode == 'b' || keyCode == 'B') {
                app.goBackInstant();
                return;
            }
            if (keyCode == 'q' || keyCode == 'Q') {
                app.switchTab(-1);
                return;
            }
            if (keyCode == 'e' || keyCode == 'E') {
                app.switchTab(1);
                return;
            }
            if (keyCode == 'u' || keyCode == 'U') {
                app.openInputBox(0, tab.url);
                return;
            }
            if (keyCode == 'g' || keyCode == 'G') {
                app.openInputBox(0, "");
                return;
            }
            if (keyCode == 't' || keyCode == 'T') {
                app.translateCurrentViewport();
                return;
            }
            if (keyCode == 'x' || keyCode == 'X') {
                app.toggleDomDistiller();
                return;
            }
            if (keyCode == 'f' || keyCode == 'F') {
                app.openInputBox(1, app.lastFindKeyword);
                return;
            }
            if (keyCode == 'l' || keyCode == 'L') {
                app.triggerSelectedLinkDownload();
                return;
            }
            if (keyCode == 'c' || keyCode == 'C') {
                app.openCopyBox("Sao chép Văn bản (Copy):", app.extractVisibleText(25));
                return;
            }
            if (keyCode == 'a' || keyCode == 'A') {
                app.toggleAutoScroll();
                return;
            }
            if (keyCode == ']') {
                app.changeChunkPage(1);
                return;
            }
            if (keyCode == '[') {
                app.changeChunkPage(-1);
                return;
            }
            if (keyCode == 'n' || keyCode == 'N') {
                if (tab.nextChapterUrl != null) {
                    app.navigateTo(tab.nextChapterUrl, true);
                } else if (tab.totalChunks > 1 && tab.currentChunk + 1 < tab.totalChunks) {
                    app.changeChunkPage(1);
                } else {
                    app.statusText = "Không có Chương sau";
                    repaint();
                }
                return;
            }
            if (keyCode == 'p' || keyCode == 'P') {
                if (tab.prevChapterUrl != null) {
                    app.navigateTo(tab.prevChapterUrl, true);
                } else if (tab.totalChunks > 1 && tab.currentChunk > 0) {
                    app.changeChunkPage(-1);
                } else {
                    app.statusText = "Không có Chương trước";
                    repaint();
                }
                return;
            }
            if (keyCode == 'd' || keyCode == 'D') {
                app.cycleTheme();
                return;
            }
            if (keyCode == 'z' || keyCode == 'Z') {
                app.cycleFontSize();
                return;
            }
            if (keyCode == 'k' || keyCode == 'K') {
                app.showBookmarkScreen();
                return;
            }
            if (keyCode == 'o' || keyCode == 'O') {
                app.showFileManagerScreen();
                return;
            }
            if (keyCode == 'r' || keyCode == 'R') {
                app.navigateTo(tab.url, false);
                return;
            }
            if (keyCode == 'm' || keyCode == 'M') {
                app.showSettings();
                return;
            }
            if (keyCode == '0' || keyCode == ' ') {
                app.fullScreen = !app.fullScreen;
                setFullScreenMode(app.fullScreen);
                repaint();
                return;
            }
            if (keyCode == 'w' || keyCode == 'W') {
                tab.jumpLink(-1);
                repaint();
                return;
            }
            if (keyCode == 's' || keyCode == 'S') {
                tab.jumpLink(1);
                repaint();
                return;
            }

            int action = 0;
            try {
                action = getGameAction(keyCode);
            } catch (Exception e) {}

            Font plainFont = app.getBodyFont(false);
            int totalLines = tab.lines.size();
            int lineH = plainFont.getHeight() + 2;
            int visibleLines = (getHeight() - headerFont.getHeight() - 16) / lineH;
            if (visibleLines < 1) visibleLines = 5;

            if (action == UP) {
                if (tab.scrollLine > 0) tab.scrollLine--;
                tab.syncSelectedLinkToViewport(visibleLines);
                repaint();
            } else if (action == DOWN) {
                if (tab.scrollLine + 1 < totalLines) tab.scrollLine++;
                tab.syncSelectedLinkToViewport(visibleLines);
                repaint();
            } else if (action == LEFT) {
                tab.scrollLine -= visibleLines;
                if (tab.scrollLine < 0) tab.scrollLine = 0;
                tab.syncSelectedLinkToViewport(visibleLines);
                repaint();
            } else if (action == RIGHT) {
                tab.scrollLine += visibleLines;
                if (tab.scrollLine >= totalLines) tab.scrollLine = Math.max(0, totalLines - 1);
                tab.syncSelectedLinkToViewport(visibleLines);
                repaint();
            } else if (action == FIRE || keyCode == 10 || keyCode == 13) {
                if (tab.selectedLink >= 0 && tab.selectedLink < tab.links.size()) {
                    String target = (String) tab.links.elementAt(tab.selectedLink);
                    app.navigateTo(target, true);
                }
            }
        }
    }
}

// =========================================================================
// MODULE 2: GoogleParser & Chromium DOM Distiller Heuristics
// =========================================================================
class GoogleParser {

    // Giải mã kết quả JSON trả về từ Google Translate GTX API
    static String parseTranslateResponse(String json) {
        if (json == null || json.length() == 0) return "";
        StringBuffer out = new StringBuffer();
        int idx = 0;
        int len = json.length();

        // Cấu trúc trả về của client=gtx: [[["Đoạn dịch 1","Gốc 1",...],["Đoạn dịch 2","Gốc 2",...]],...]
        int firstArrayEnd = json.indexOf("]],");
        if (firstArrayEnd != -1) {
            len = firstArrayEnd + 2;
        }

        while (idx < len) {
            int startSeg = json.indexOf("[\"", idx);
            if (startSeg == -1 || startSeg >= len) break;

            int pos = startSeg + 2;
            StringBuffer seg = new StringBuffer();
            while (pos < len) {
                char c = json.charAt(pos);
                if (c == '\\' && pos + 1 < len) {
                    char next = json.charAt(pos + 1);
                    if (next == 'n') seg.append('\n');
                    else if (next == 'r') {}
                    else if (next == '"') seg.append('"');
                    else if (next == '\\') seg.append('\\');
                    else seg.append(next);
                    pos += 2;
                } else if (c == '"') {
                    break;
                } else {
                    seg.append(c);
                    pos++;
                }
            }
            out.append(seg.toString()).append(" ");
            int nextBracket = json.indexOf("]", pos);
            idx = (nextBracket != -1) ? (nextBracket + 1) : (pos + 1);
        }

        String res = out.toString().trim();
        return res.length() > 0 ? res : "Không thể bóc tách dữ liệu dịch.";
    }
}

// =========================================================================
// MODULE 3: NetRouter - Bộ định tuyến Omnibox, Giải mã Link & Nhận diện File
// =========================================================================
class NetRouter {

    static boolean isBinaryDownloadUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        int q = lower.indexOf('?');
        if (q != -1) lower = lower.substring(0, q);
        return lower.endsWith(".jar") || lower.endsWith(".jad") ||
               lower.endsWith(".sis") || lower.endsWith(".sisx") ||
               lower.endsWith(".zip") || lower.endsWith(".rar") ||
               lower.endsWith(".mp3") || lower.endsWith(".wgz");
    }

    static String buildNavigationTarget(String input, int connMode, int searchEngine, String workerUrl) {
        boolean isUrl = (input.indexOf('.') > 0 && input.indexOf(' ') == -1);
        if (isUrl) {
            if (!input.startsWith("http://") && !input.startsWith("https://")) {
                return "https://" + input;
            }
            return input;
        }
        String encoded = urlEncode(input);
        if (connMode == 3 && workerUrl != null && workerUrl.length() > 8) {
            return workerUrl + "?q=" + encoded;
        }
        if (searchEngine == 1) {
            return "https://www.google.com/search?q=" + encoded + "&hl=vi";
        } else if (searchEngine == 2) {
            return "https://vi.m.wikipedia.org/w/index.php?search=" + encoded;
        }
        return "https://html.duckduckgo.com/html/?q=" + encoded;
    }

    static String buildFetchUrl(String targetUrl, int connMode, String workerUrl) {
        if (connMode == 0 || connMode == 1) {
            if (targetUrl.startsWith("https://r.jina.ai/")) {
                return targetUrl;
            }
            return "https://r.jina.ai/" + targetUrl;
        } else if (connMode == 3 && workerUrl != null && workerUrl.length() > 8) {
            if (targetUrl.startsWith(workerUrl)) {
                return targetUrl;
            }
            String sep = (workerUrl.indexOf('?') == -1) ? "?url=" : "&url=";
            return workerUrl + sep + urlEncode(targetUrl);
        }
        return targetUrl;
    }

    static String resolveUrl(String baseUrl, String href) {
        href = replaceAllStr(href, "&amp;", "&").trim();

        int uddg = href.indexOf("uddg=");
        if (uddg != -1) {
            String sub = href.substring(uddg + 5);
            int amp = sub.indexOf('&');
            if (amp != -1) sub = sub.substring(0, amp);
            return urlDecode(sub);
        }

        if (href.startsWith("/url?q=") || href.indexOf("google.com/url?q=") != -1) {
            int qPos = href.indexOf("?q=");
            String sub = href.substring(qPos + 3);
            int amp = sub.indexOf('&');
            if (amp != -1) sub = sub.substring(0, amp);
            return urlDecode(sub);
        }

        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        if (href.startsWith("//")) {
            return "https:" + href;
        }

        String cleanBase = baseUrl;
        if (cleanBase.startsWith("https://r.jina.ai/")) {
            cleanBase = cleanBase.substring(18);
        }
        int schemeEnd = cleanBase.indexOf("://");
        String origin = cleanBase;
        if (schemeEnd != -1) {
            int slash = cleanBase.indexOf('/', schemeEnd + 3);
            if (slash != -1) {
                origin = cleanBase.substring(0, slash);
            }
        }
        if (href.startsWith("/")) {
            return origin + href;
        }
        return origin + "/" + href;
    }

    static String getHost(String url) {
        if (url == null) return "";
        if (url.startsWith("chrome://")) return url;
        int s = url.indexOf("://");
        if (s != -1) url = url.substring(s + 3);
        int e = url.indexOf('/');
        if (e != -1) url = url.substring(0, e);
        return url;
    }

    static String extractFileName(String url) {
        String clean = url;
        int q = clean.indexOf('?');
        if (q != -1) clean = clean.substring(0, q);
        int slash = clean.lastIndexOf('/');
        String name = (slash != -1 && slash + 1 < clean.length()) ? clean.substring(slash + 1) : "download.bin";
        StringBuffer safe = new StringBuffer();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_') {
                safe.append(c);
            } else {
                safe.append('_');
            }
        }
        return safe.length() > 0 ? safe.toString() : "file_" + (System.currentTimeMillis() % 10000) + ".bin";
    }

    static String replaceAllStr(String src, String target, String replacement) {
        if (src == null || target == null || target.length() == 0) return src;
        StringBuffer sb = new StringBuffer();
        int idx = 0;
        int pos;
        while ((pos = src.indexOf(target, idx)) != -1) {
            sb.append(src.substring(idx, pos));
            sb.append(replacement);
            idx = pos + target.length();
        }
        sb.append(src.substring(idx));
        return sb.toString();
    }

    static String urlEncode(String s) {
        StringBuffer sb = new StringBuffer();
        try {
            byte[] bytes = s.getBytes("UTF-8");
            for (int i = 0; i < bytes.length; i++) {
                int b = bytes[i] & 0xFF;
                if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') ||
                    b == '-' || b == '_' || b == '.') {
                    sb.append((char) b);
                } else if (b == ' ') {
                    sb.append('+');
                } else {
                    sb.append('%');
                    String hex = Integer.toHexString(b).toUpperCase();
                    if (hex.length() < 2) sb.append('0');
                    sb.append(hex);
                }
            }
        } catch (Exception e) {}
        return sb.toString();
    }

    static String urlDecode(String s) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '+') {
                baos.write(' ');
            } else if (c == '%' && i + 2 < len) {
                try {
                    int v = Integer.parseInt(s.substring(i + 1, i + 3), 16);
                    baos.write(v);
                    i += 2;
                } catch (Exception e) {
                    baos.write(c);
                }
            } else {
                baos.write((byte) c);
            }
        }
        try {
            return new String(baos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return new String(baos.toByteArray());
        }
    }
}

// =========================================================================
// MODULE 4: HttpEngine - Kết nối TLS 1.3 kèm Chromium Data Saver & CookieJar
// =========================================================================
class HttpEngine {

    private static final int MAX_STREAM_BYTES = 131072; // 128KB (4 Chunk x 32KB)

    static String fetchSmallString(OmniBrowserE72 app, String url) throws Exception {
        HttpConnection conn = null;
        InputStream is = null;
        try {
            conn = openWithRedirect(app, url, 2, 0);
            is = conn.openInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int r;
            int total = 0;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
                total += r;
                if (total >= 16384) break;
            }
            byte[] raw = baos.toByteArray();
            try {
                return new String(raw, "UTF-8");
            } catch (Exception e) {
                return new String(raw);
            }
        } finally {
            try { if (is != null) is.close(); } catch (Exception e) {}
            try { if (conn != null) conn.close(); } catch (Exception e) {}
        }
    }

    static String fetchDocument(OmniBrowserE72 app, String url, int connMode, int uaProfile) throws Exception {
        HttpConnection conn = null;
        InputStream is = null;
        try {
            conn = openWithRedirect(app, url, connMode, uaProfile);
            int rc = conn.getResponseCode();
            app.statusText = "Đang nhận dữ liệu (HTTP " + rc + ")...";
            app.loadProgress = 30;
            app.canvas.repaint();

            is = conn.openInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int read;
            int total = 0;

            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
                total += read;
                app.loadProgress = 30 + Math.min(50, (total * 50) / MAX_STREAM_BYTES);
                app.statusText = "Đã tải: " + (total / 1024) + " KB";
                app.canvas.repaint();
                if (total >= MAX_STREAM_BYTES) {
                    break;
                }
            }

            byte[] rawBytes = baos.toByteArray();
            baos = null;
            try {
                return new String(rawBytes, "UTF-8");
            } catch (Exception e) {
                return new String(rawBytes);
            }
        } finally {
            try { if (is != null) is.close(); } catch (Exception e) {}
            try { if (conn != null) conn.close(); } catch (Exception e) {}
        }
    }

    static HttpConnection openWithRedirect(OmniBrowserE72 app, String url, int connMode, int uaProfile) throws Exception {
        String current = url;
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpConnection c = (HttpConnection) Connector.open(current, Connector.READ, true);
            c.setRequestMethod(HttpConnection.GET);

            if (uaProfile == 1) {
                c.setRequestProperty("User-Agent", "Lynx/2.8.9rel.1 libwww-FM/2.14 SSL-MM/1.4.1 OpenSSL/1.1.1");
            } else if (uaProfile == 2) {
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (SymbianOS/9.3; Series60/3.2 NokiaE72-1/091.003; Profile/MIDP-2.1 Configuration/CLDC-1.1) AppleWebKit/525 (KHTML, like Gecko) Version/3.0 BrowserNG/7.2.6.2");
            } else {
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            }

            // Chromium Data Saver / Client Hints
            if (app.flagSaveData) {
                c.setRequestProperty("Save-Data", "on");
                c.setRequestProperty("Sec-CH-UA-Mobile", "?1");
            }

            if (connMode == 0 || connMode == 1) {
                c.setRequestProperty("X-Return-Format", "markdown");
            }

            String host = NetRouter.getHost(current);
            String savedCookie = (String) app.cookieJar.get(host);
            if (savedCookie != null) {
                c.setRequestProperty("Cookie", savedCookie);
            }

            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,text/plain,*/*");
            c.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8");
            c.setRequestProperty("Connection", "close");

            int code = c.getResponseCode();

            String setCookie = c.getHeaderField("Set-Cookie");
            if (setCookie != null && host.length() > 0) {
                int semi = setCookie.indexOf(';');
                String cleanCookie = (semi != -1) ? setCookie.substring(0, semi).trim() : setCookie.trim();
                if (cleanCookie.length() > 0) {
                    app.cookieJar.put(host, cleanCookie);
                }
            }

            if (code == HttpConnection.HTTP_MOVED_PERM ||
                code == HttpConnection.HTTP_MOVED_TEMP ||
                code == HttpConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308) {

                String loc = c.getHeaderField("Location");
                c.close();
                if (loc == null) break;
                current = NetRouter.resolveUrl(current, loc);
                app.statusText = "Chuyển hướng (" + (redirects + 1) + ")...";
                app.canvas.repaint();
            } else {
                return c;
            }
        }
        throw new Exception("Quá nhiều Redirect");
    }
}

// =========================================================================
// MODULE 5: TabDocument - Micro-DOM + Thuật toán Chromium DOM Distiller
// =========================================================================
class TabDocument {

    private static final int CHUNK_SIZE = 32768;

    int tabId;
    String url = "https://vnexpress.net";
    String fullRawCache = "";
    int currentChunk = 0;
    int totalChunks = 1;

    Vector history = new Vector();
    Vector lines = new Vector();
    Vector links = new Vector();
    int[] lineLinkMap = new int[0];
    int scrollLine = 0;
    int selectedLink = -1;
    String nextChapterUrl = null;
    String prevChapterUrl = null;

    static class LineItem {
        String text;
        int linkIndex;
        boolean isBold;
        LineItem(String t, int l, boolean b) {
            this.text = t;
            this.linkIndex = l;
            this.isBold = b;
        }
    }

    static class HistoryEntry {
        String url;
        String cachedRaw;
        int scrollLine;
        int selectedLink;
        int chunkIndex;
        HistoryEntry(String u, String raw, int sc, int sel, int ch) {
            this.url = u;
            this.cachedRaw = raw;
            this.scrollLine = sc;
            this.selectedLink = sel;
            this.chunkIndex = ch;
        }
    }

    TabDocument(int id) {
        this.tabId = id;
    }

    void renderHomeSpeedDial() {
        lines.removeAllElements();
        links.removeAllElements();
        nextChapterUrl = null;
        prevChapterUrl = null;
        currentChunk = 0;
        totalChunks = 1;

        addLine("OMNICHROMIUM E72 v5.0 (GOOGLE EDITION)", -1, true);
        addLine("• [U]/[G]: Google Omnibox (Có Gợi ý Từ khóa)", -1, false);
        addLine("• [T]: Dịch trang sang Tiếng Việt (Google GTX)", -1, false);
        addLine("• [X]: Bật/Tắt Chromium DOM Distiller (Lọc rác)", -1, false);
        addLine("• [Q]/[E]: Đổi Tab | [W]/[S]: Chọn Link | [B]: Lùi 0ms", -1, false);
        addLine("• [N]/[P]: Chương Sau/Trước | [L]: Tải File về E:/", -1, false);
        addLine("--------------------------------------------", -1, false);

        addLink("Tìm kiếm Google/Web (Vượt chặn)", "https://html.duckduckgo.com/html/");
        addLink("Sáng Tác Việt (Truyện Dịch/Convert)", "https://sangtacviet.com");
        addLink("VnExpress Tin nhanh", "https://vnexpress.net");
        addLink("Wikipedia Tiếng Việt", "https://vi.m.wikipedia.org");
        addLink("Cổ Thi Văn - Hán Học (Gushiwen)", "https://m.gushiwen.cn");
        addLink("Trung Y Cổ Tịch (CText.org)", "https://ctext.org");
        addLink("Thông tin Hệ thống & RAM (chrome://version)", "chrome://version");
        addLink("Lịch sử Duyệt web (chrome://history)", "chrome://history");

        rebuildMap();
        selectedLink = 0;
        scrollLine = 0;
    }

    void addLine(String text, int linkIdx, boolean bold) {
        lines.addElement(new LineItem(text, linkIdx, bold));
    }

    void addLink(String label, String targetUrl) {
        int idx = links.size();
        links.addElement(targetUrl);
        lines.addElement(new LineItem("[" + (idx + 1) + "] " + label, idx, false));
    }

    void rebuildMap() {
        int sz = lines.size();
        lineLinkMap = new int[sz];
        for (int i = 0; i < sz; i++) {
            LineItem item = (LineItem) lines.elementAt(i);
            lineLinkMap[i] = item.linkIndex;
        }
    }

    void recalculateChunks() {
        int len = (fullRawCache != null) ? fullRawCache.length() : 0;
        totalChunks = (len <= CHUNK_SIZE) ? 1 : ((len + CHUNK_SIZE - 1) / CHUNK_SIZE);
        if (currentChunk >= totalChunks) currentChunk = 0;
    }

    void loadDocument(String raw, String sourceUrl, Font font, int maxWidth, boolean useDistiller) {
        this.url = sourceUrl;
        this.fullRawCache = raw != null ? raw : "";
        this.currentChunk = 0;
        recalculateChunks();
        parseAndWrapChunk(font, maxWidth, useDistiller);
    }

    void parseAndWrapChunk(Font font, int maxWidth, boolean useDistiller) {
        lines.removeAllElements();
        links.removeAllElements();
        nextChapterUrl = null;
        prevChapterUrl = null;

        int totalLen = fullRawCache.length();
        int startPos = currentChunk * CHUNK_SIZE;
        int endPos = Math.min(totalLen, startPos + CHUNK_SIZE);
        String text = (startPos < totalLen) ? fullRawCache.substring(startPos, endPos) : "";

        if (text.indexOf("<body") != -1 || text.indexOf("<html") != -1 || text.indexOf("<div") != -1 || text.indexOf("<p>") != -1) {
            text = stripBlockTag(text, "<script", "</script>");
            text = stripBlockTag(text, "<style", "</style>");
            text = stripBlockTag(text, "<svg", "</svg>");
            text = stripBlockTag(text, "<head", "</head>");
            text = stripBlockTag(text, "<noscript", "</noscript>");
            if (useDistiller) {
                // Chromium DOM Distiller: Cắt bỏ khối điều hướng, chân trang, thanh bên
                text = stripBlockTag(text, "<nav", "</nav>");
                text = stripBlockTag(text, "<footer", "</footer>");
                text = stripBlockTag(text, "<aside", "</aside>");
                text = stripBlockTag(text, "<header", "</header>");
                text = stripBlockTag(text, "<form", "</form>");
            }
            text = convertHtmlLinksToTokens(text, url);
            text = stripAllTagsAndNormalize(text);
        } else {
            text = convertMarkdownLinksToTokens(text, url);
        }

        text = decodeHtmlEntities(text);

        int len = text.length();
        int lineStart = 0;
        int emptyCount = 0;
        int consecutiveShortLinks = 0;

        for (int i = 0; i <= len; i++) {
            if (i == len || text.charAt(i) == '\n') {
                String line = text.substring(lineStart, i).trim();
                lineStart = i + 1;

                if (line.length() == 0) {
                    emptyCount++;
                    if (emptyCount <= 1) {
                        addLine("", -1, false);
                    }
                    continue;
                }
                emptyCount = 0;

                boolean isHeading = false;
                if (line.startsWith("#")) {
                    while (line.startsWith("#")) {
                        line = line.substring(1).trim();
                    }
                    isHeading = true;
                }

                int linkIdx = -1;
                if (line.startsWith("@#L")) {
                    int colon = line.indexOf(':');
                    if (colon > 3) {
                        try {
                            linkIdx = Integer.parseInt(line.substring(3, colon));
                            String labelOnly = line.substring(colon + 1).trim();

                            // Chromium DOM Distiller Link-Density Filter:
                            // Nếu bật Distiller, bỏ qua các chuỗi menu ngắn liên tiếp (dưới 18 ký tự) trừ nút chuyển chương
                            if (useDistiller && labelOnly.length() < 18 && !isNavigationKeyword(labelOnly)) {
                                consecutiveShortLinks++;
                                if (consecutiveShortLinks > 2) {
                                    continue;
                                }
                            } else {
                                consecutiveShortLinks = 0;
                            }
                            line = "[" + (linkIdx + 1) + "] " + labelOnly;
                        } catch (Exception e) {
                            linkIdx = -1;
                        }
                    }
                } else {
                    consecutiveShortLinks = 0;
                }
                wrapLineToVector(line, linkIdx, isHeading, font, maxWidth);
            }
        }

        if (totalChunks > 1) {
            addLine("--- [Phần " + (currentChunk + 1) + "/" + totalChunks + " - Bấm phím ] xem tiếp, [ để lùi] ---", -1, true);
        }
        rebuildMap();
    }

    private boolean isNavigationKeyword(String s) {
        String lower = s.toLowerCase();
        return lower.indexOf("chương") != -1 || lower.indexOf("tiếp") != -1 ||
               lower.indexOf("trước") != -1 || lower.indexOf("next") != -1 ||
               lower.indexOf("prev") != -1 || lower.indexOf("章") != -1;
    }

    private void registerDetectedLink(String label, String fullUrl) {
        String lower = label.toLowerCase();
        if (lower.indexOf("chương sau") != -1 || lower.indexOf("chuong sau") != -1 ||
            lower.indexOf("chương tiếp") != -1 || lower.indexOf("tiếp theo") != -1 ||
            lower.equals("tiếp") || lower.equals("next") || lower.indexOf("下一章") != -1 || lower.indexOf("下页") != -1) {
            if (nextChapterUrl == null) nextChapterUrl = fullUrl;
        }
        if (lower.indexOf("chương trước") != -1 || lower.indexOf("chuong truoc") != -1 ||
            lower.equals("trước") || lower.equals("prev") || lower.indexOf("上一章") != -1 || lower.indexOf("上页") != -1) {
            if (prevChapterUrl == null) prevChapterUrl = fullUrl;
        }
    }

    Vector wrapStandaloneText(String text, Font font, int maxWidth) {
        Vector result = new Vector();
        StringBuffer currentLine = new StringBuffer();
        int start = 0;
        int len = text.length();

        while (start < len) {
            int spaceIdx = text.indexOf(' ', start);
            String word;
            if (spaceIdx == -1) {
                word = text.substring(start);
                start = len;
            } else {
                word = text.substring(start, spaceIdx + 1);
                start = spaceIdx + 1;
            }

            if (font.stringWidth(currentLine.toString() + word) <= maxWidth) {
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    result.addElement(currentLine.toString().trim());
                    currentLine.setLength(0);
                }
                currentLine.append(word);
            }
        }
        if (currentLine.length() > 0) {
            result.addElement(currentLine.toString().trim());
        }
        return result;
    }

    private void wrapLineToVector(String text, int linkIdx, boolean bold, Font font, int maxWidth) {
        if (font.stringWidth(text) <= maxWidth) {
            addLine(text, linkIdx, bold);
            return;
        }
        StringBuffer currentLine = new StringBuffer();
        int start = 0;
        int len = text.length();

        while (start < len) {
            int spaceIdx = text.indexOf(' ', start);
            String word;
            if (spaceIdx == -1) {
                word = text.substring(start);
                start = len;
            } else {
                word = text.substring(start, spaceIdx + 1);
                start = spaceIdx + 1;
            }

            if (font.stringWidth(currentLine.toString() + word) <= maxWidth) {
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    addLine(currentLine.toString().trim(), linkIdx, bold);
                    currentLine.setLength(0);
                }
                while (font.stringWidth(word) > maxWidth && word.length() > 2) {
                    int cut = word.length() / 2;
                    while (cut > 1 && font.stringWidth(word.substring(0, cut)) > maxWidth) {
                        cut--;
                    }
                    addLine(word.substring(0, cut), linkIdx, bold);
                    word = word.substring(cut);
                }
                currentLine.append(word);
            }
        }
        if (currentLine.length() > 0) {
            addLine(currentLine.toString().trim(), linkIdx, bold);
        }
    }

    private String convertMarkdownLinksToTokens(String src, String baseUrl) {
        StringBuffer out = new StringBuffer();
        int idx = 0;
        int len = src.length();

        while (idx < len) {
            int openBracket = src.indexOf('[', idx);
            if (openBracket == -1) {
                out.append(src.substring(idx));
                break;
            }
            if (openBracket > 0 && src.charAt(openBracket - 1) == '!') {
                out.append(src.substring(idx, openBracket + 1));
                idx = openBracket + 1;
                continue;
            }
            int closeBracket = src.indexOf("](", openBracket);
            if (closeBracket == -1 || closeBracket - openBracket > 220) {
                out.append(src.substring(idx, openBracket + 1));
                idx = openBracket + 1;
                continue;
            }
            int closeParen = src.indexOf(')', closeBracket + 2);
            if (closeParen == -1 || closeParen - closeBracket > 450) {
                out.append(src.substring(idx, openBracket + 1));
                idx = openBracket + 1;
                continue;
            }

            out.append(src.substring(idx, openBracket));
            String label = src.substring(openBracket + 1, closeBracket).trim();
            String href = src.substring(closeBracket + 2, closeParen).trim();

            if (label.length() > 0 && href.length() > 1 && !href.startsWith("javascript:")) {
                String fullUrl = NetRouter.resolveUrl(baseUrl, href);
                registerDetectedLink(label, fullUrl);
                int linkId = links.size();
                links.addElement(fullUrl);
                out.append("\n@#L").append(linkId).append(":").append(label).append("\n");
            } else {
                out.append(label);
            }
            idx = closeParen + 1;
        }
        return out.toString();
    }

    private String convertHtmlLinksToTokens(String html, String baseUrl) {
        StringBuffer out = new StringBuffer();
        String lower = html.toLowerCase();
        int idx = 0;
        int len = html.length();

        while (idx < len) {
            int aStart = lower.indexOf("<a ", idx);
            if (aStart == -1) {
                out.append(html.substring(idx));
                break;
            }
            int tagClose = lower.indexOf('>', aStart);
            int aEnd = lower.indexOf("</a>", tagClose);
            if (tagClose == -1 || aEnd == -1 || aEnd - aStart > 1000) {
                out.append(html.substring(idx, aStart + 3));
                idx = aStart + 3;
                continue;
            }

            out.append(html.substring(idx, aStart));
            String aTag = html.substring(aStart, tagClose + 1);
            String innerText = stripAllTagsAndNormalize(html.substring(tagClose + 1, aEnd)).trim();
            String href = extractAttr(aTag, "href");

            if (href != null && href.length() > 1 && innerText.length() > 0 && !href.startsWith("javascript:") && !href.startsWith("#")) {
                String fullUrl = NetRouter.resolveUrl(baseUrl, href);
                registerDetectedLink(innerText, fullUrl);
                int linkId = links.size();
                links.addElement(fullUrl);
                out.append("\n@#L").append(linkId).append(":").append(innerText).append("\n");
            } else {
                out.append(innerText);
            }
            idx = aEnd + 4;
        }
        return out.toString();
    }

    private String extractAttr(String tag, String attrName) {
        String lower = tag.toLowerCase();
        int pos = lower.indexOf(attrName + "=");
        if (pos == -1) return null;
        pos += attrName.length() + 1;
        while (pos < tag.length() && tag.charAt(pos) == ' ') pos++;
        if (pos >= tag.length()) return null;

        char quote = tag.charAt(pos);
        if (quote == '"' || quote == '\'') {
            int end = tag.indexOf(quote, pos + 1);
            if (end != -1) return tag.substring(pos + 1, end).trim();
        } else {
            int end = tag.indexOf(' ', pos);
            if (end == -1) end = tag.indexOf('>', pos);
            if (end != -1) return tag.substring(pos, end).trim();
        }
        return null;
    }

    private String stripBlockTag(String html, String openTag, String closeTag) {
        StringBuffer sb = new StringBuffer();
        String lower = html.toLowerCase();
        int idx = 0;
        while (idx < html.length()) {
            int start = lower.indexOf(openTag, idx);
            if (start == -1) {
                sb.append(html.substring(idx));
                break;
            }
            sb.append(html.substring(idx, start));
            int end = lower.indexOf(closeTag, start);
            if (end == -1) break;
            idx = end + closeTag.length();
        }
        return sb.toString();
    }

    private String stripAllTagsAndNormalize(String html) {
        StringBuffer sb = new StringBuffer();
        boolean inTag = false;
        StringBuffer tagBuf = new StringBuffer();

        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '<') {
                inTag = true;
                tagBuf.setLength(0);
            } else if (c == '>') {
                inTag = false;
                String t = tagBuf.toString().toLowerCase().trim();
                if (t.startsWith("h1") || t.startsWith("h2") || t.startsWith("h3")) {
                    sb.append("\n# ");
                } else if (t.startsWith("p") || t.startsWith("/p") || t.startsWith("br") ||
                    t.startsWith("div") || t.startsWith("/div") || t.startsWith("li") ||
                    t.startsWith("tr") || t.startsWith("/h1") || t.startsWith("/h2") || t.startsWith("/h3")) {
                    sb.append('\n');
                }
            } else if (inTag) {
                if (tagBuf.length() < 12) tagBuf.append(c);
            } else {
                if (c != '\r') sb.append(c);
            }
        }
        return sb.toString();
    }

    private String decodeHtmlEntities(String s) {
        s = NetRouter.replaceAllStr(s, "&nbsp;", " ");
        s = NetRouter.replaceAllStr(s, "&quot;", "\"");
        s = NetRouter.replaceAllStr(s, "&apos;", "'");
        s = NetRouter.replaceAllStr(s, "&#39;", "'");
        s = NetRouter.replaceAllStr(s, "&lt;", "<");
        s = NetRouter.replaceAllStr(s, "&gt;", ">");
        s = NetRouter.replaceAllStr(s, "&amp;", "&");
        return s;
    }

    String buildPlainTextDump() {
        StringBuffer sb = new StringBuffer();
        sb.append("URL: ").append(url).append("\r\n");
        sb.append("----------------------------------------\r\n");
        for (int i = 0; i < lines.size(); i++) {
            LineItem item = (LineItem) lines.elementAt(i);
            sb.append(item.text).append("\r\n");
        }
        return sb.toString();
    }

    void jumpLink(int dir) {
        if (links.size() == 0) return;
        selectedLink += dir;
        if (selectedLink < 0) selectedLink = links.size() - 1;
        if (selectedLink >= links.size()) selectedLink = 0;

        for (int i = 0; i < lineLinkMap.length; i++) {
            if (lineLinkMap[i] == selectedLink) {
                scrollLine = Math.max(0, i - 2);
                break;
            }
        }
    }

    void syncSelectedLinkToViewport(int visibleLines) {
        for (int i = scrollLine; i < scrollLine + visibleLines && i < lineLinkMap.length; i++) {
            if (lineLinkMap[i] >= 0) {
                selectedLink = lineLinkMap[i];
                return;
            }
        }
    }
}

// =========================================================================
// MODULE 6: StorageEngine - Trình Tải File Nhị Phân & Quản lý Thẻ nhớ E:/
// =========================================================================
class StorageEngine {

    private static String resolveStorageRoot(String folderName) {
        FileConnection fc = null;
        String root = "file:///E:/" + folderName + "/";
        try {
            fc = (FileConnection) Connector.open(root, Connector.READ_WRITE);
            if (!fc.exists()) fc.mkdir();
            fc.close();
            return root;
        } catch (Exception e) {
            try { if (fc != null) fc.close(); } catch (Exception ex) {}
        }
        root = "file:///C:/Data/" + folderName + "/";
        try {
            fc = (FileConnection) Connector.open(root, Connector.READ_WRITE);
            if (!fc.exists()) fc.mkdir();
            fc.close();
        } catch (Exception e) {
            try { if (fc != null) fc.close(); } catch (Exception ex) {}
        }
        return root;
    }

    static void downloadBinaryFile(final OmniBrowserE72 app, final String fileUrl) {
        Thread downloader = new Thread(new Runnable() {
            public void run() {
                app.isLoading = true;
                app.loadProgress = 5;
                String fName = NetRouter.extractFileName(fileUrl);
                app.statusText = "Đang tải file: " + fName + "...";
                app.canvas.repaint();

                HttpConnection hc = null;
                InputStream is = null;
                FileConnection fc = null;
                OutputStream os = null;

                try {
                    String cleanUrl = fileUrl;
                    if (cleanUrl.startsWith("https://r.jina.ai/")) {
                        cleanUrl = cleanUrl.substring(18);
                    }
                    hc = HttpEngine.openWithRedirect(app, cleanUrl, 2, 0);
                    long len = hc.getLength();
                    is = hc.openInputStream();

                    String downloadDir = resolveStorageRoot("Download");
                    String destPath = downloadDir + fName;
                    fc = (FileConnection) Connector.open(destPath, Connector.READ_WRITE);
                    if (!fc.exists()) {
                        fc.create();
                    }
                    os = fc.openOutputStream();

                    byte[] buf = new byte[4096];
                    int r;
                    long total = 0;
                    while ((r = is.read(buf)) != -1) {
                        os.write(buf, 0, r);
                        total += r;
                        if (len > 0) {
                            app.loadProgress = (int) ((total * 100) / len);
                        } else {
                            app.loadProgress = (app.loadProgress + 10) % 95;
                        }
                        app.statusText = "Đang tải " + fName + ": " + (total / 1024) + " KB";
                        app.canvas.repaint();
                    }
                    os.flush();
                    app.statusText = "Đã tải xong: " + destPath.substring(8) + " (" + (total / 1024) + "KB)";
                } catch (Exception e) {
                    app.statusText = "Lỗi tải file: " + e.getMessage();
                } finally {
                    try { if (os != null) os.close(); } catch (Exception e) {}
                    try { if (fc != null) fc.close(); } catch (Exception e) {}
                    try { if (is != null) is.close(); } catch (Exception e) {}
                    try { if (hc != null) hc.close(); } catch (Exception e) {}
                    app.isLoading = false;
                    app.loadProgress = 0;
                    app.canvas.repaint();
                }
            }
        });
        downloader.start();
    }

    static void exportTabToFile(final OmniBrowserE72 app, final TabDocument tab, final boolean asRawHtml) {
        final String content = asRawHtml ? tab.fullRawCache : tab.buildPlainTextDump();
        final String ext = asRawHtml ? ".html" : ".txt";
        final String host = NetRouter.replaceAllStr(NetRouter.getHost(tab.url), ".", "_");

        Thread writer = new Thread(new Runnable() {
            public void run() {
                app.statusText = "Đang ghi thẻ nhớ E:/OmniE72/...";
                app.canvas.repaint();
                FileConnection fc = null;
                OutputStream os = null;
                try {
                    String baseDir = resolveStorageRoot("OmniE72");
                    String fName = baseDir + host + "_" + (System.currentTimeMillis() % 100000) + ext;
                    fc = (FileConnection) Connector.open(fName, Connector.READ_WRITE);
                    if (!fc.exists()) fc.create();
                    os = fc.openOutputStream();
                    byte[] bytes = content.getBytes("UTF-8");
                    os.write(bytes);
                    os.flush();
                    app.statusText = "Đã xuất: " + fName.substring(8);
                } catch (Exception e) {
                    app.statusText = "Lỗi ghi file: " + e.getMessage();
                } finally {
                    try { if (os != null) os.close(); } catch (Exception e) {}
                    try { if (fc != null) fc.close(); } catch (Exception e) {}
                    app.canvas.repaint();
                }
            }
        });
        writer.start();
    }

    static void scanSdCardFolder(final OmniBrowserE72 app) {
        Thread scanner = new Thread(new Runnable() {
            public void run() {
                FileConnection fc = null;
                try {
                    String baseDir = resolveStorageRoot("OmniE72");
                    fc = (FileConnection) Connector.open(baseDir, Connector.READ);
                    Enumeration en = fc.list();
                    app.sdCardFiles.removeAllElements();
                    while (en.hasMoreElements()) {
                        String name = (String) en.nextElement();
                        if (!name.endsWith("/")) {
                            app.sdCardFiles.addElement(name);
                        }
                    }
                    app.statusText = "Tìm thấy " + app.sdCardFiles.size() + " file trong E:/OmniE72/";
                } catch (Exception e) {
                    app.statusText = "Lỗi quét thẻ nhớ: " + e.getMessage();
                } finally {
                    try { if (fc != null) fc.close(); } catch (Exception e) {}
                    app.showFileManagerScreen();
                }
            }
        });
        scanner.start();
    }

    static void readSdCardFile(final OmniBrowserE72 app, final String fileName) {
        Thread reader = new Thread(new Runnable() {
            public void run() {
                app.statusText = "Đang đọc E:/OmniE72/" + fileName + "...";
                app.canvas.repaint();
                FileConnection fc = null;
                InputStream is = null;
                try {
                    String baseDir = resolveStorageRoot("OmniE72");
                    fc = (FileConnection) Connector.open(baseDir + fileName, Connector.READ);
                    is = fc.openInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[2048];
                    int r;
                    while ((r = is.read(buf)) != -1) {
                        baos.write(buf, 0, r);
                    }
                    String text;
                    try {
                        text = new String(baos.toByteArray(), "UTF-8");
                    } catch (Exception e) {
                        text = new String(baos.toByteArray());
                    }
                    app.loadRawDocumentIntoCurrentTab("file:///E:/OmniE72/" + fileName, text, "Đang đọc: " + fileName);
                } catch (Exception e) {
                    app.statusText = "Lỗi mở file: " + e.getMessage();
                    app.canvas.repaint();
                } finally {
                    try { if (is != null) is.close(); } catch (Exception e) {}
                    try { if (fc != null) fc.close(); } catch (Exception e) {}
                }
            }
        });
        reader.start();
    }
}
