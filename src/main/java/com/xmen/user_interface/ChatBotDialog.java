package com.xmen.user_interface;

import com.xmen.service.chatbot.ChatBotService;
import com.xmen.service.chatbot.ChatBotService.Reply;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Glass chat panel that grows out of the anchor button.
 *
 * <p>Now supports markdown-lite formatting (bullets, bold), delete-thread buttons in the
 * sidebar, and an explicit "New Chat" affordance. The previous "online · offline mode" tag
 * has been removed because it confused users — the chat panel is always local.
 */
@Slf4j
public final class ChatBotDialog {

  private static final double PANEL_WIDTH = 660;
  private static final double PANEL_HEIGHT = 580;
  private static final double GAP_ABOVE_BUTTON = 10;
  private static final double SCREEN_MARGIN = 12;

  private static final List<ChatSession> SESSIONS = new ArrayList<>();
  private static ChatSession activeSession;

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(SESSIONS::clear, "chat-sessions-purge"));
  }

  private ChatBotDialog() {}

  public static void show(Stage owner, Button anchor) {
    if (anchor == null) {
      log.warn("ChatBotDialog.show called with null anchor — ignoring.");
      return;
    }
    if (anchor.getScene() == null || anchor.getScene().getWindow() == null) {
      log.warn("Anchor button has no scene/window yet — chat panel cannot open.");
      return;
    }

    Object existing = anchor.getProperties().get("chatPopup");
    if (existing instanceof Popup p && p.isShowing()) {
      p.hide();
      return;
    }

    try {
      ChatBotService bot = ChatBotService.getInstance();

      if (activeSession == null) {
        activeSession = newSession(bot);
        SESSIONS.add(activeSession);
      }

      Popup popup = new Popup();
      popup.setAutoHide(true);
      popup.setHideOnEscape(true);
      popup.setAutoFix(true);

      StackPane root = buildRoot(bot, anchor, popup);
      popup.getContent().setAll(root);

      Bounds onScreen = anchor.localToScreen(anchor.getBoundsInLocal());
      if (onScreen == null) {
        log.warn("Anchor button not laid out yet — cannot compute popup position.");
        return;
      }
      double x = onScreen.getMaxX() - PANEL_WIDTH;
      double y = onScreen.getMinY() - PANEL_HEIGHT - GAP_ABOVE_BUTTON;

      Rectangle2D screen = pickScreenFor(onScreen);
      if (screen != null) {
        if (x < screen.getMinX() + SCREEN_MARGIN) x = screen.getMinX() + SCREEN_MARGIN;
        if (x + PANEL_WIDTH > screen.getMaxX() - SCREEN_MARGIN)
          x = screen.getMaxX() - PANEL_WIDTH - SCREEN_MARGIN;
        if (y < screen.getMinY() + SCREEN_MARGIN) {
          y = onScreen.getMaxY() + GAP_ABOVE_BUTTON;
        }
      }

      popup.show(owner, x, y);

      root.setOpacity(0);
      root.setTranslateY(40);
      TranslateTransition slide = new TranslateTransition(Duration.millis(220), root);
      slide.setFromY(root.getTranslateY());
      slide.setToY(0);
      slide.setInterpolator(Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0));
      FadeTransition fade = new FadeTransition(Duration.millis(180), root);
      fade.setFromValue(0);
      fade.setToValue(1);
      new ParallelTransition(slide, fade).play();

      anchor.getProperties().put("chatPopup", popup);
      popup.setOnHidden(e -> anchor.getProperties().remove("chatPopup"));
    } catch (Throwable t) {
      log.error("Failed to open chat panel", t);
    }
  }

  // ---------------------------------------------------------------------- //
  //  Session lifecycle                                                     //
  // ---------------------------------------------------------------------- //

  private static final String DISCLAIMER =
      "A small note: I'm a local X-Men agent rather than a large language model, "
          + "so I work best with short keyword queries (for example, \"forget mutation\" "
          + "or \"add rule\") instead of long sentences. Thanks for understanding!";

  private static ChatSession newSession(ChatBotService bot) {
    ChatSession s = new ChatSession();
    s.title = "New chat";
    s.messages.add(new ChatMessage(false, bot.greeting()));
    s.messages.add(new ChatMessage(false, DISCLAIMER));
    s.pendingFollowups = bot.initialFollowups();
    return s;
  }

  // ---------------------------------------------------------------------- //
  //  Panel construction                                                    //
  // ---------------------------------------------------------------------- //

  private static StackPane buildRoot(ChatBotService bot, Button anchor, Popup popup) {
    HBox card = buildCard(bot, popup);

    StackPane root = new StackPane(card);
    root.setPickOnBounds(false);
    root.setPrefSize(PANEL_WIDTH, PANEL_HEIGHT);
    root.setMaxSize(PANEL_WIDTH, PANEL_HEIGHT);
    root.getStyleClass().add("x-root");

    Scene parentScene = anchor.getScene();
    if (parentScene != null && parentScene.getRoot() != null) {
      String inline = parentScene.getRoot().getStyle();
      if (inline != null && !inline.isBlank()) root.setStyle(inline);
    }

    if (parentScene != null) {
      root.getStylesheets().addAll(parentScene.getStylesheets());
    } else {
      URL css = ChatBotDialog.class.getResource("/css/main-v2.css");
      if (css != null) root.getStylesheets().add(css.toExternalForm());
    }
    return root;
  }

  private static HBox buildCard(ChatBotService bot, Popup popup) {
    VBox sidebar = new VBox(10);
    sidebar.setPadding(new Insets(14, 10, 14, 12));
    sidebar.setPrefWidth(190);
    sidebar.setMinWidth(190);
    sidebar.getStyleClass().add("x-chat-sidebar");

    Label sideTitle = new Label("Chats");
    sideTitle.getStyleClass().add("x-chat-side-title");

    Button newChat = new Button("+ New Chat");
    newChat.getStyleClass().add("x-chat-newchat");
    newChat.setFocusTraversable(false);
    newChat.setMaxWidth(Double.MAX_VALUE);

    VBox threadList = new VBox(4);
    ScrollPane threadScroll = new ScrollPane(threadList);
    threadScroll.setFitToWidth(true);
    threadScroll.getStyleClass().add("x-chat-scroll");
    threadScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    VBox.setVgrow(threadScroll, Priority.ALWAYS);

    sidebar.getChildren().addAll(sideTitle, newChat, threadScroll);

    Label title = new Label("X-Men Assistant");
    title.getStyleClass().add("x-chat-title");

    VBox headerText = new VBox(2, title);
    HBox.setHgrow(headerText, Priority.ALWAYS);

    Button close = new Button();
    close.setGraphic(closeIcon());
    close.getStyleClass().add("x-chat-close");
    close.setFocusTraversable(false);
    close.setOnAction(e -> popup.hide());

    HBox header = new HBox(headerText, close);
    header.setAlignment(Pos.CENTER_LEFT);

    VBox messageColumn = new VBox(10);
    messageColumn.setPadding(new Insets(8, 4, 8, 4));
    // Stash the popup on the message column so addBubble can reach it when the
    // copy icon is clicked — needed to suppress the popup's auto-hide while the
    // "Message Content Copied" toast briefly steals focus.
    messageColumn.getProperties().put("xmen.chatPopup", popup);
    ScrollPane scroll = new ScrollPane(messageColumn);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("x-chat-scroll");
    VBox.setVgrow(scroll, Priority.ALWAYS);
    messageColumn.heightProperty().addListener((obs, oldV, newV) -> scroll.setVvalue(1.0));

    FlowPane chipsBar = new FlowPane(8, 8);

    TextField input = new TextField();
    input.setPromptText("Ask about mutations, depth modes, derivation…");
    input.getStyleClass().add("x-chat-input");
    HBox.setHgrow(input, Priority.ALWAYS);

    Button send = new Button();
    send.setGraphic(sendIcon());
    send.getStyleClass().add("x-chat-send");
    send.setFocusTraversable(false);

    HBox inputRow = new HBox(8, input, send);
    inputRow.setAlignment(Pos.CENTER);

    VBox mainCol = new VBox(12, header, scroll, chipsBar, inputRow);
    mainCol.getStyleClass().add("x-chat-card");
    mainCol.setPadding(new Insets(18, 18, 16, 18));
    HBox.setHgrow(mainCol, Priority.ALWAYS);

    HBox card = new HBox(sidebar, mainCol);
    card.getStyleClass().add("x-chat-shell");
    card.setPrefSize(PANEL_WIDTH, PANEL_HEIGHT);
    card.setMaxSize(PANEL_WIDTH, PANEL_HEIGHT);

    Runnable refreshThreadList = () -> renderThreadList(bot, threadList, messageColumn, chipsBar, input);
    refreshThreadList.run();
    renderTranscript(messageColumn, chipsBar, input, bot);

    newChat.setOnAction(e -> {
      activeSession = newSession(bot);
      SESSIONS.add(activeSession);
      refreshThreadList.run();
      renderTranscript(messageColumn, chipsBar, input, bot);
      Platform.runLater(input::requestFocus);
    });

    final Runnable[] submitHolder = new Runnable[1];
    submitHolder[0] = () -> {
      String text = input.getText();
      if (text == null || text.isBlank()) return;
      input.clear();
      sendUserMessage(text, bot, messageColumn, chipsBar, input, refreshThreadList);
    };
    Runnable submit = submitHolder[0];

    send.setOnAction(e -> submit.run());
    input.setOnAction(e -> submit.run());

    Platform.runLater(input::requestFocus);
    return card;
  }

  private static void sendUserMessage(String text, ChatBotService bot, VBox messageColumn,
                                       FlowPane chipsBar, TextField input,
                                       Runnable refreshThreadList) {
    ChatSession s = activeSession;
    if (s == null) return;
    s.messages.add(new ChatMessage(true, text));
    if ("New chat".equals(s.title)) {
      s.title = text.length() > 28 ? text.substring(0, 26) + "…" : text;
      refreshThreadList.run();
    }
    addBubble(messageColumn, text, true);
    chipsBar.getChildren().clear();

    Thread worker = new Thread(() -> {
      final Reply reply;
      try {
        reply = bot.respondReply(text);
      } catch (Throwable t) {
        log.warn("Chatbot reply failed", t);
        Platform.runLater(() -> addBubble(messageColumn,
            "Something went wrong while answering. Check the logs.", false));
        return;
      }
      Platform.runLater(() -> {
        s.messages.add(new ChatMessage(false, reply.text));
        s.pendingFollowups = reply.followups;
        addBubble(messageColumn, reply.text, false);
        renderChips(chipsBar, reply.followups, bot, messageColumn, input, refreshThreadList);
      });
    }, "chatbot-reply");
    worker.setDaemon(true);
    worker.start();
  }

  private static void renderTranscript(VBox messageColumn, FlowPane chipsBar,
                                        TextField input, ChatBotService bot) {
    messageColumn.getChildren().clear();
    if (activeSession == null) return;
    for (ChatMessage m : activeSession.messages) addBubble(messageColumn, m.text, m.fromUser);
    renderChips(chipsBar, activeSession.pendingFollowups, bot, messageColumn, input, () -> {});
  }

  private static void renderChips(FlowPane chipsBar, List<String> followups, ChatBotService bot,
                                   VBox messageColumn, TextField input,
                                   Runnable refreshThreadList) {
    chipsBar.getChildren().clear();
    if (followups == null) return;
    for (String f : followups) {
      Button chip = new Button(f);
      chip.getStyleClass().add("x-chat-chip");
      chip.setFocusTraversable(false);
      chip.setOnAction(e -> sendUserMessage(f, bot, messageColumn, chipsBar, input, refreshThreadList));
      chipsBar.getChildren().add(chip);
    }
  }

  private static void renderThreadList(ChatBotService bot, VBox threadList,
                                        VBox messageColumn, FlowPane chipsBar, TextField input) {
    threadList.getChildren().clear();
    for (int i = SESSIONS.size() - 1; i >= 0; i--) {
      ChatSession s = SESSIONS.get(i);

      Label name = new Label(s.title);
      name.getStyleClass().add("x-chat-thread-title");
      name.setMaxWidth(108);
      Label time = new Label(DateTimeFormatter.ofPattern("HH:mm")
          .format(LocalTime.ofInstant(s.createdAt, ZoneId.systemDefault())));
      time.getStyleClass().add("x-chat-thread-time");

      // Per-thread delete button. Wraps a trash glyph; on click it removes the
      // session from the list. If we deleted the active session we reset to
      // the first remaining thread (or open a fresh one if none are left).
      Button del = new Button();
      del.setGraphic(Icons.trash(14, Color.WHITE));
      del.getStyleClass().add("x-chat-thread-delete");
      del.setFocusTraversable(false);
      del.setOnAction(ev -> {
        ev.consume();
        SESSIONS.remove(s);
        if (s == activeSession) {
          activeSession = SESSIONS.isEmpty() ? null : SESSIONS.get(SESSIONS.size() - 1);
          if (activeSession == null) {
            activeSession = newSession(bot);
            SESSIONS.add(activeSession);
          }
        }
        renderThreadList(bot, threadList, messageColumn, chipsBar, input);
        renderTranscript(messageColumn, chipsBar, input, bot);
      });

      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      HBox row = new HBox(6, name, spacer, time, del);
      row.setAlignment(Pos.CENTER_LEFT);

      // Tapping the row (anywhere but the delete button) opens the thread.
      row.getStyleClass().add("x-chat-thread");
      if (s == activeSession) row.getStyleClass().add("x-chat-thread-active");
      row.setOnMouseClicked(e -> {
        activeSession = s;
        renderTranscript(messageColumn, chipsBar, input, bot);
        renderThreadList(bot, threadList, messageColumn, chipsBar, input);
      });
      threadList.getChildren().add(row);
    }
  }

  /**
   * Render a chat bubble. Bot replies render with markdown-lite: lines starting with "-" or
   * "*" become bullet rows; lines starting with "## " or "**…**" become bold. This stops
   * paragraph answers from collapsing into a single squashed line, which was the complaint
   * about the previous renderer.
   */
  private static void addBubble(VBox column, String text, boolean fromUser) {
    Node bubble;
    if (fromUser) {
      TextFlow flow = new TextFlow();
      flow.setMaxWidth(420);
      flow.getStyleClass().add("x-chat-bubble-user");
      // Padding handled by .x-chat-bubble-user CSS rule.
      for (Text node : renderInline(text, true)) flow.getChildren().add(node);
      bubble = flow;
    } else {
      VBox col = new VBox(2);
      col.getStyleClass().add("x-chat-bubble-bot");
      col.setMaxWidth(440);
      // Padding (including the generous right-side reserve for the copy icon
      // overlay) is set by the .x-chat-bubble-bot CSS rule — setting it from
      // Java was a no-op because the CSS pass overrode it.
      renderBotBody(col, text);
      bubble = col;
    }

    attachCopyMenu(bubble, text);

    Node packed;
    if (fromUser) {
      packed = bubble;
    } else {
      // Bot bubbles get an unobtrusive copy glyph anchored to the bubble's
      // top-right. It renders in the text colour, no background, so it reads
      // as a hint icon rather than a chunky button.
      Button copyBtn = new Button();
      copyBtn.setGraphic(copyGlyph());
      copyBtn.getStyleClass().add("x-chat-copy-icon");
      copyBtn.setFocusTraversable(false);
      Tooltip.install(copyBtn, new Tooltip("Copy message"));
      copyBtn.setOnAction(e -> {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);

        // Showing the ThemedToast spawns a new always-on-top Stage which
        // briefly takes focus. The chat container is a JavaFX Popup with
        // setAutoHide(true), and losing focus to the toast triggers that
        // auto-hide — closing the whole chat panel. Suppress autoHide while
        // the toast is on screen, then restore it.
        Popup chatPopup = (Popup) column.getProperties().get("xmen.chatPopup");
        boolean previousAutoHide = chatPopup != null && chatPopup.isAutoHide();
        if (chatPopup != null) chatPopup.setAutoHide(false);

        Window owner =
            copyBtn.getScene() != null ? copyBtn.getScene().getWindow() : null;
        if (owner instanceof Popup p && p.getOwnerWindow() != null) {
          owner = p.getOwnerWindow();
        }
        ThemedToast.show(owner, "Message Content Copied", 1200);

        if (chatPopup != null && previousAutoHide) {
          // Restore auto-hide once the toast has had time to fade out. Use a
          // small buffer past the toast's 1200 ms so a focus event during the
          // toast's dismissal animation doesn't sneak through.
          javafx.animation.PauseTransition restore =
              new javafx.animation.PauseTransition(Duration.millis(1600));
          restore.setOnFinished(ev -> chatPopup.setAutoHide(true));
          restore.play();
        }
      });

      StackPane overlay = new StackPane(bubble, copyBtn);
      overlay.setAlignment(Pos.TOP_RIGHT);
      // Padding offsets the icon slightly inside the bubble's top-right corner.
      StackPane.setMargin(copyBtn, new Insets(4, 6, 0, 0));
      // The bubble itself stays left-aligned; the StackPane only hosts the
      // overlay positioning of the icon.
      StackPane.setAlignment(bubble, Pos.TOP_LEFT);
      overlay.setPickOnBounds(false);
      packed = overlay;
    }

    HBox row = new HBox(packed);
    row.setAlignment(fromUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    row.setPadding(new Insets(0, 2, 0, 2));
    column.getChildren().add(row);
  }

  /** Small two-square "copy" glyph, stroked in the current text colour via CSS. */
  private static SVGPath copyGlyph() {
    SVGPath p = new SVGPath();
    // Two offset rectangles: back sheet (top-right) + front sheet (bottom-left).
    p.setContent(
        "M9 3 h11 v11 h-11 z "
            + "M4 8 h11 v11 h-11 z");
    p.setStroke(Color.WHITE); // CSS .x-chat-copy-icon overrides via -fx-stroke
    p.setStrokeWidth(1.6);
    p.setFill(Color.TRANSPARENT);
    p.setScaleX(0.7);
    p.setScaleY(0.7);
    p.getStyleClass().add("x-chat-copy-glyph");
    return p;
  }

  /**
   * Make a rendered bubble copy-pastable. JavaFX {@link TextFlow}s aren't natively
   * selectable, so we surface the raw message via a right-click "Copy" menu and a
   * Ctrl+C shortcut once the bubble has focus.
   */
  private static void attachCopyMenu(Node bubble, String text) {
    Runnable copyToClipboard = () -> {
      ClipboardContent content = new ClipboardContent();
      content.putString(text);
      Clipboard.getSystemClipboard().setContent(content);
    };

    MenuItem copyItem = new MenuItem("Copy message");
    copyItem.setOnAction(e -> copyToClipboard.run());
    ContextMenu menu = new ContextMenu(copyItem);

    bubble.setOnContextMenuRequested(e -> {
      menu.show(bubble, e.getScreenX(), e.getScreenY());
      e.consume();
    });

    bubble.setFocusTraversable(true);
    bubble.setOnMouseClicked(e -> bubble.requestFocus());
    bubble.setOnKeyPressed(e -> {
      if (e.isShortcutDown() && e.getCode() == KeyCode.C) {
        copyToClipboard.run();
        e.consume();
      }
    });
  }

  /**
   * Lay out the bot bubble's body as a stack of rows: paragraphs render as TextFlow with
   * inline bold/code, bullet lines render with a coloured dot + their text.
   */
  private static void renderBotBody(VBox col, String src) {
    if (src == null) return;
    String[] lines = src.split("\n", -1);
    StringBuilder paragraph = new StringBuilder();
    Runnable flushParagraph = () -> {
      if (paragraph.length() == 0) return;
      TextFlow tf = new TextFlow();
      for (Text t : renderInline(paragraph.toString(), false)) tf.getChildren().add(t);
      col.getChildren().add(tf);
      paragraph.setLength(0);
    };
    for (String raw : lines) {
      String line = raw == null ? "" : raw;
      String trimmed = line.trim();
      if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
        flushParagraph.run();
        Label dot = new Label("•");
        dot.getStyleClass().add("x-chat-bullet-dot");
        TextFlow body = new TextFlow();
        for (Text t : renderInline(trimmed.substring(2), false)) body.getChildren().add(t);
        HBox bullet = new HBox(8, dot, body);
        HBox.setHgrow(body, Priority.ALWAYS);
        bullet.getStyleClass().add("x-chat-bullet-row");
        col.getChildren().add(bullet);
      } else if (trimmed.isEmpty()) {
        flushParagraph.run();
        Region gap = new Region();
        gap.setMinHeight(4);
        col.getChildren().add(gap);
      } else {
        if (paragraph.length() > 0) paragraph.append('\n');
        paragraph.append(line);
      }
    }
    flushParagraph.run();
  }

  /**
   * Inline tokeniser: `code spans`, **bold spans**, and plain text. Newlines preserved as
   * literal break nodes so multi-line paragraphs read naturally.
   */
  private static List<Text> renderInline(String src, boolean userBubble) {
    List<Text> out = new ArrayList<>();
    if (src == null) return out;
    int i = 0;
    int n = src.length();
    StringBuilder buf = new StringBuilder();
    Runnable flush = () -> {
      if (buf.length() == 0) return;
      Text t = new Text(buf.toString());
      t.getStyleClass().add("x-chat-text");
      out.add(t);
      buf.setLength(0);
    };
    while (i < n) {
      char c = src.charAt(i);
      if (c == '`') {
        int end = src.indexOf('`', i + 1);
        if (end < 0) {
          buf.append(c);
          i++;
        } else {
          flush.run();
          Text t = new Text(src.substring(i + 1, end));
          t.getStyleClass().add("x-chat-text-code");
          out.add(t);
          i = end + 1;
        }
      } else if (c == '*' && i + 1 < n && src.charAt(i + 1) == '*') {
        int end = src.indexOf("**", i + 2);
        if (end < 0) {
          buf.append(c);
          i++;
        } else {
          flush.run();
          Text t = new Text(src.substring(i + 2, end));
          t.getStyleClass().addAll("x-chat-text", "x-chat-text-bold");
          out.add(t);
          i = end + 2;
        }
      } else {
        buf.append(c);
        i++;
      }
    }
    flush.run();
    return out;
  }

  private static Rectangle2D pickScreenFor(Bounds onScreen) {
    for (Screen s : Screen.getScreens()) {
      Rectangle2D b = s.getVisualBounds();
      if (b.contains(onScreen.getCenterX(), onScreen.getCenterY())) return b;
    }
    Screen primary = Screen.getPrimary();
    return primary != null ? primary.getVisualBounds() : null;
  }

  private static SVGPath closeIcon() {
    SVGPath p = new SVGPath();
    p.setContent("M6 6 L18 18 M18 6 L6 18");
    p.setStroke(Color.WHITE);
    p.setStrokeWidth(2.0);
    p.setFill(Color.TRANSPARENT);
    p.setScaleX(0.7);
    p.setScaleY(0.7);
    p.getStyleClass().add("x-icon-themed");
    return p;
  }

  private static SVGPath sendIcon() {
    SVGPath p = new SVGPath();
    p.setContent("M2 12 L22 3 L18 22 L13 14 L2 12 Z M13 14 L22 3");
    p.setStroke(Color.WHITE);
    p.setStrokeWidth(2.0);
    p.setFill(Color.TRANSPARENT);
    p.setScaleX(0.85);
    p.setScaleY(0.85);
    p.getStyleClass().add("x-icon-themed");
    return p;
  }

  private static final class ChatSession {
    final Instant createdAt = Instant.now();
    String title;
    final List<ChatMessage> messages = new ArrayList<>();
    List<String> pendingFollowups = List.of();
  }

  private static final class ChatMessage {
    final boolean fromUser;
    final String text;
    ChatMessage(boolean fromUser, String text) {
      this.fromUser = fromUser;
      this.text = text;
    }
  }
}
