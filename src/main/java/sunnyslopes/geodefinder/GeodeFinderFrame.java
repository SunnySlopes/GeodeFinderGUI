package sunnyslopes.geodefinder;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class GeodeFinderFrame extends JFrame {

    private static final int DEFAULT_MIN = -16384;
    private static final int DEFAULT_MAX = 16383;
    private static final int DEFAULT_LIST_MIN = -4096;
    private static final int DEFAULT_LIST_MAX = 4095;
    private static final int BOUNDARY_MIN = -30000000;
    private static final int BOUNDARY_MAX = 30000000;
    private static final int DEFAULT_GEODE = 20;
    private static final int DEFAULT_BUDDING = 800;
    private static final int DEFAULT_RADIUS = 6;

    private ResourceBundle messages;
    private Locale currentLocale;
    private Font loadedFont;
    private JTabbedPane tabbedPane;

    private JTextField searchSeedField;
    private JTextField searchThreadCountField;
    private JComboBox<String> searchVersionCombo;
    private JTextField searchGeodeField;
    private JTextField searchBuddingField;
    private JTextField searchRadiusField;
    private JCheckBox searchStructureCheck;
    private JCheckBox searchFastCheck;
    private JTextField minXField;
    private JTextField maxXField;
    private JTextField minZField;
    private JTextField maxZField;
    private JCheckBox squareSideCheckBox;
    private JTextField squareSideField;
    private boolean squareSideUpdating;
    private JComboBox<String> languageComboBox;
    private JButton searchStartButton;
    private JButton searchPauseButton;
    private JButton searchStopButton;
    private JButton searchResetButton;
    private JButton searchWorldBorderButton;
    private JProgressBar searchProgressBar;
    private JLabel searchElapsedLabel;
    private JLabel searchRemainingLabel;
    private JLabel searchCreditLabel;
    private JTextArea searchResultArea;
    private boolean searchRunning;
    private boolean searchPaused;
    private GeodeSearchRunner searchRunner;
    private final AtomicLong searchToken = new AtomicLong();

    private JButton listFileButton;
    private JLabel listFileLabel;
    private JTextField listThreadCountField;
    private JComboBox<String> listVersionCombo;
    private JTextField listGeodeField;
    private JTextField listBuddingField;
    private JTextField listRadiusField;
    private JCheckBox listStructureCheck;
    private JCheckBox listFastCheck;
    private JTextField listMinXField;
    private JTextField listMaxXField;
    private JTextField listMinZField;
    private JTextField listMaxZField;
    private JCheckBox listSquareSideCheckBox;
    private JTextField listSquareSideField;
    private boolean listSquareSideUpdating;
    private JButton listStartButton;
    private JButton listPauseButton;
    private JButton listStopButton;
    private JButton listResetButton;
    private JProgressBar listProgressBar;
    private JLabel listElapsedLabel;
    private JLabel listSeedProgressLabel;
    private JLabel listRemainingLabel;
    private JLabel listCreditLabel;
    private JTextArea listResultArea;
    private File selectedSeedFile;
    private boolean listRunning;
    private boolean listPaused;
    private GeodeSearchRunner listRunner;
    private final AtomicLong listToken = new AtomicLong();
    private volatile long listPausedAccum;
    private volatile long listPauseStarted;

    public GeodeFinderFrame() {
        Locale system = Locale.getDefault();
        if ("zh".equals(system.getLanguage())) {
            String country = system.getCountry().toLowerCase(Locale.ROOT);
            currentLocale = ("cn".equals(country) || "hk".equals(country) || "tw".equals(country))
                ? new Locale("zh", "CN") : new Locale("en", "US");
        } else {
            currentLocale = new Locale("en", "US");
        }
        try {
            messages = ResourceBundle.getBundle("messages", currentLocale);
        } catch (RuntimeException ex) {
            currentLocale = new Locale("en", "US");
            messages = ResourceBundle.getBundle("messages", currentLocale);
        }

        setTitle(getString("window.title"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setWindowIcon();
        setChineseFont();

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab(getString("tab.singleSeedSearch"), createSingleSeedPanel());
        tabbedPane.addTab(getString("tab.listSearch"), createListPanel());
        add(tabbedPane, BorderLayout.CENTER);
        pack();
        setSize(1500, 1000);
        setLocationRelativeTo(null);
    }

    private JPanel createSingleSeedPanel() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel leftPanel = new JPanel(new BorderLayout());
        JPanel input = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = formConstraints();
        int row = 0;
        searchSeedField = new JTextField("", 20);
        addRow(input, gbc, row++, "label.seed", searchSeedField);
        searchThreadCountField = new JTextField(String.valueOf(Runtime.getRuntime().availableProcessors()), 20);
        addRow(input, gbc, row++, "label.threadCount", searchThreadCountField);
        searchGeodeField = new JTextField(String.valueOf(DEFAULT_GEODE), 20);
        addRow(input, gbc, row++, "label.geodeThreshold", searchGeodeField);
        searchBuddingField = new JTextField(String.valueOf(DEFAULT_BUDDING), 20);
        addRow(input, gbc, row++, "label.buddingThreshold", searchBuddingField);
        searchRadiusField = new JTextField(String.valueOf(DEFAULT_RADIUS), 20);
        addRow(input, gbc, row++, "label.loadedRadius", searchRadiusField);

        minXField = new JTextField(String.valueOf(DEFAULT_MIN), 20);
        maxXField = new JTextField(String.valueOf(DEFAULT_MAX), 20);
        minZField = new JTextField(String.valueOf(DEFAULT_MIN), 20);
        maxZField = new JTextField(String.valueOf(DEFAULT_MAX), 20);
        addRow(input, gbc, row++, "label.minX", minXField);
        addRow(input, gbc, row++, "label.maxX", maxXField);
        addRow(input, gbc, row++, "label.minZ", minZField);
        addRow(input, gbc, row++, "label.maxZ", maxZField);

        squareSideCheckBox = taggedCheck("label.squareSide", true);
        squareSideField = new JTextField(String.valueOf((long) DEFAULT_MAX - DEFAULT_MIN + 1), 20);
        squareSideCheckBox.addActionListener(e -> {
            applyBoundEnable(true);
            if (squareSideCheckBox.isSelected()) {
                applySquareSide(false);
            }
        });
        squareSideField.getDocument().addDocumentListener(simpleListener(() -> applySquareSide(false)));
        addSideBySide(input, gbc, row++, squareSideCheckBox, squareSideField);

        searchVersionCombo = new JComboBox<>(GeodeSearchRunner.VERSIONS);
        searchVersionCombo.setSelectedIndex(GeodeSearchRunner.DEFAULT_VERSION);
        addRow(input, gbc, row++, "label.mcVersion", searchVersionCombo);

        searchStructureCheck = taggedCheck("label.structureOnly", false);
        searchFastCheck = taggedCheck("label.fastSearch", false);
        wireModeChecks(searchStructureCheck, searchFastCheck, searchBuddingField);
        addFullWidth(input, gbc, row++, searchStructureCheck);
        addFullWidth(input, gbc, row++, searchFastCheck);

        languageComboBox = new JComboBox<>(new String[] {"中文", "English"});
        languageComboBox.setSelectedIndex("zh".equals(currentLocale.getLanguage()) ? 0 : 1);
        languageComboBox.addActionListener(e -> changeLanguage());
        addRow(input, gbc, row, "label.language", languageComboBox);

        searchStartButton = taggedButton("button.startSearch");
        searchPauseButton = taggedButton("button.pause");
        searchStopButton = taggedButton("button.stop");
        searchResetButton = taggedButton("button.reset");
        searchWorldBorderButton = taggedButton("button.resetToWorldBorder");
        searchPauseButton.setEnabled(false);
        searchStopButton.setEnabled(false);
        searchStartButton.addActionListener(e -> startSearch());
        searchPauseButton.addActionListener(e -> toggleSearchPause());
        searchStopButton.addActionListener(e -> stopSearch());
        searchResetButton.addActionListener(e -> applySearchBounds(DEFAULT_MIN, DEFAULT_MAX, DEFAULT_MIN, DEFAULT_MAX));
        searchWorldBorderButton.addActionListener(e -> applySearchBounds(BOUNDARY_MIN, BOUNDARY_MAX, BOUNDARY_MIN, BOUNDARY_MAX));

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(searchStartButton);
        buttonPanel.add(searchPauseButton);
        buttonPanel.add(searchStopButton);
        buttonPanel.add(searchResetButton);
        buttonPanel.add(searchWorldBorderButton);

        searchCreditLabel = new JLabel(buildCreditText());
        searchCreditLabel.setFont(getLoadedFont());
        JPanel creditPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        creditPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        creditPanel.add(searchCreditLabel);
        JPanel creditButtonPanel = new JPanel(new BorderLayout());
        creditButtonPanel.add(creditPanel, BorderLayout.NORTH);
        creditButtonPanel.add(buttonPanel, BorderLayout.SOUTH);

        searchProgressBar = new JProgressBar(0, 100);
        searchProgressBar.setStringPainted(true);
        searchProgressBar.setString(getString("progress.format", 0, 0, 0.0));
        searchElapsedLabel = new JLabel(getString("elapsedTime", formatTime(0)));
        searchElapsedLabel.setFont(getLoadedFont());
        searchRemainingLabel = new JLabel(getString("remainingTime.calculating"));
        searchRemainingLabel.setFont(getLoadedFont());
        JPanel progressPanel = new JPanel(new GridBagLayout());
        GridBagConstraints pg = formConstraints();
        pg.gridx = 0;
        pg.gridy = 0;
        pg.gridwidth = 2;
        progressPanel.add(searchProgressBar, pg);
        pg.gridwidth = 1;
        pg.gridy = 1;
        progressPanel.add(searchElapsedLabel, pg);
        pg.gridy = 2;
        progressPanel.add(searchRemainingLabel, pg);

        leftPanel.add(input, BorderLayout.NORTH);
        leftPanel.add(creditButtonPanel, BorderLayout.CENTER);
        JPanel leftBottomPanel = new JPanel(new BorderLayout());
        leftBottomPanel.add(progressPanel, BorderLayout.NORTH);
        JPanel leftContainer = new JPanel(new BorderLayout());
        leftContainer.add(leftPanel, BorderLayout.CENTER);
        leftContainer.add(leftBottomPanel, BorderLayout.SOUTH);

        searchResultArea = new JTextArea();
        searchResultArea.setEditable(false);
        searchResultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(searchResultArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        JPanel right = new JPanel(new BorderLayout());
        right.putClientProperty("i18n.border", "result.border");
        right.setBorder(BorderFactory.createTitledBorder(getString("result.border")));
        JButton export = taggedButton("button.export");
        JButton sort = taggedButton("button.sort");
        export.addActionListener(e -> exportText(searchResultArea.getText(), getString("file.searchOutput")));
        sort.addActionListener(e -> searchResultArea.setText(sortLines(searchResultArea.getText())));
        JPanel exportBar = new JPanel(new FlowLayout());
        exportBar.add(export);
        exportBar.add(sort);
        right.add(scrollPane, BorderLayout.CENTER);
        right.add(exportBar, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftContainer, right);
        split.setDividerLocation(600);
        split.setResizeWeight(0.5);
        main.add(split, BorderLayout.CENTER);
        applyBoundEnable(true);
        return main;
    }

    private JPanel createListPanel() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel leftPanel = new JPanel(new BorderLayout());
        JPanel input = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = formConstraints();
        int row = 0;

        JLabel listFileTitle = new JLabel(getString("label.seedFile"));
        listFileTitle.setFont(getLoadedFont());
        listFileTitle.putClientProperty("i18n", "label.seedFile");
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        input.add(listFileTitle, gbc);
        listFileButton = taggedButton("button.selectFile");
        listFileLabel = new JLabel(getString("label.noFileSelected"));
        listFileLabel.setFont(getLoadedFont());
        listFileButton.addActionListener(e -> chooseSeedFile());
        JPanel seedFilePanel = new JPanel(new BorderLayout());
        seedFilePanel.add(listFileButton, BorderLayout.WEST);
        seedFilePanel.add(listFileLabel, BorderLayout.CENTER);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        input.add(seedFilePanel, gbc);
        row++;

        listThreadCountField = new JTextField(String.valueOf(Runtime.getRuntime().availableProcessors()), 20);
        addRow(input, gbc, row++, "label.threadCount", listThreadCountField);
        listGeodeField = new JTextField(String.valueOf(DEFAULT_GEODE), 20);
        addRow(input, gbc, row++, "label.geodeThreshold", listGeodeField);
        listBuddingField = new JTextField(String.valueOf(DEFAULT_BUDDING), 20);
        addRow(input, gbc, row++, "label.buddingThreshold", listBuddingField);
        listRadiusField = new JTextField(String.valueOf(DEFAULT_RADIUS), 20);
        addRow(input, gbc, row++, "label.loadedRadius", listRadiusField);

        listMinXField = new JTextField(String.valueOf(DEFAULT_LIST_MIN), 20);
        listMaxXField = new JTextField(String.valueOf(DEFAULT_LIST_MAX), 20);
        listMinZField = new JTextField(String.valueOf(DEFAULT_LIST_MIN), 20);
        listMaxZField = new JTextField(String.valueOf(DEFAULT_LIST_MAX), 20);
        addRow(input, gbc, row++, "label.minX", listMinXField);
        addRow(input, gbc, row++, "label.maxX", listMaxXField);
        addRow(input, gbc, row++, "label.minZ", listMinZField);
        addRow(input, gbc, row++, "label.maxZ", listMaxZField);
        listSquareSideCheckBox = taggedCheck("label.squareSide", true);
        listSquareSideField = new JTextField(String.valueOf((long) DEFAULT_LIST_MAX - DEFAULT_LIST_MIN + 1), 20);
        listSquareSideCheckBox.addActionListener(e -> {
            applyListBoundEnable(true);
            if (listSquareSideCheckBox.isSelected()) {
                applySquareSide(true);
            }
        });
        listSquareSideField.getDocument().addDocumentListener(simpleListener(() -> applySquareSide(true)));
        addSideBySide(input, gbc, row++, listSquareSideCheckBox, listSquareSideField);

        listVersionCombo = new JComboBox<>(GeodeSearchRunner.VERSIONS);
        listVersionCombo.setSelectedIndex(GeodeSearchRunner.DEFAULT_VERSION);
        addRow(input, gbc, row++, "label.mcVersion", listVersionCombo);
        listStructureCheck = taggedCheck("label.structureOnly", false);
        listFastCheck = taggedCheck("label.fastSearch", false);
        wireModeChecks(listStructureCheck, listFastCheck, listBuddingField);
        addFullWidth(input, gbc, row++, listStructureCheck);
        addFullWidth(input, gbc, row, listFastCheck);

        listStartButton = taggedButton("button.startSearch");
        listPauseButton = taggedButton("button.pause");
        listStopButton = taggedButton("button.stop");
        listResetButton = taggedButton("button.resetList");
        listPauseButton.setEnabled(false);
        listStopButton.setEnabled(false);
        listStartButton.addActionListener(e -> startListSearch());
        listPauseButton.addActionListener(e -> toggleListPause());
        listStopButton.addActionListener(e -> stopListSearch());
        listResetButton.addActionListener(e -> applyListBounds(DEFAULT_LIST_MIN, DEFAULT_LIST_MAX, DEFAULT_LIST_MIN, DEFAULT_LIST_MAX));
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(listStartButton);
        buttonPanel.add(listPauseButton);
        buttonPanel.add(listStopButton);
        buttonPanel.add(listResetButton);

        listCreditLabel = new JLabel(buildCreditText());
        listCreditLabel.setFont(getLoadedFont());
        JPanel creditPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        creditPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        creditPanel.add(listCreditLabel);
        JPanel creditButtonPanel = new JPanel(new BorderLayout());
        creditButtonPanel.add(creditPanel, BorderLayout.NORTH);
        creditButtonPanel.add(buttonPanel, BorderLayout.SOUTH);

        listProgressBar = new JProgressBar(0, 100);
        listProgressBar.setStringPainted(true);
        listProgressBar.setString(getString("progress.seedsDone", 0, 0));
        listElapsedLabel = new JLabel(getString("elapsedTime", formatTime(0)));
        listElapsedLabel.setFont(getLoadedFont());
        listSeedProgressLabel = new JLabel(getString("currentSeed.default"));
        listSeedProgressLabel.setFont(getLoadedFont());
        listRemainingLabel = new JLabel(getString("remainingTime.calculating"));
        listRemainingLabel.setFont(getLoadedFont());
        JPanel progressPanel = new JPanel(new GridBagLayout());
        GridBagConstraints pg = formConstraints();
        pg.gridx = 0;
        pg.gridy = 0;
        pg.gridwidth = 2;
        progressPanel.add(listProgressBar, pg);
        pg.gridwidth = 1;
        pg.gridy = 1;
        progressPanel.add(listElapsedLabel, pg);
        pg.gridy = 2;
        progressPanel.add(listSeedProgressLabel, pg);
        pg.gridy = 3;
        progressPanel.add(listRemainingLabel, pg);

        leftPanel.add(input, BorderLayout.NORTH);
        leftPanel.add(creditButtonPanel, BorderLayout.CENTER);
        JPanel leftBottomPanel = new JPanel(new BorderLayout());
        leftBottomPanel.add(progressPanel, BorderLayout.CENTER);
        JPanel leftContainer = new JPanel(new BorderLayout());
        leftContainer.add(leftPanel, BorderLayout.CENTER);
        leftContainer.add(leftBottomPanel, BorderLayout.SOUTH);

        listResultArea = new JTextArea();
        listResultArea.setEditable(false);
        listResultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(listResultArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        JPanel right = new JPanel(new BorderLayout());
        right.putClientProperty("i18n.border", "result.border");
        right.setBorder(BorderFactory.createTitledBorder(getString("result.border")));
        JButton export = taggedButton("button.export");
        JButton exportSeeds = taggedButton("button.exportSeedList");
        JButton sortCount = taggedButton("button.sortByCount");
        JButton sortDistance = taggedButton("button.sortByDistance");
        export.addActionListener(e -> exportText(listResultArea.getText(), getString("file.searchOutput")));
        exportSeeds.addActionListener(e -> exportSeedList());
        sortCount.addActionListener(e -> listResultArea.setText(sortSeedGroups(true)));
        sortDistance.addActionListener(e -> listResultArea.setText(sortSeedGroups(false)));
        JPanel exportBar = new JPanel(new FlowLayout());
        exportBar.add(export);
        exportBar.add(exportSeeds);
        exportBar.add(sortCount);
        exportBar.add(sortDistance);
        right.add(scrollPane, BorderLayout.CENTER);
        right.add(exportBar, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftContainer, right);
        split.setDividerLocation(600);
        split.setResizeWeight(0.5);
        main.add(split, BorderLayout.CENTER);
        applyListBoundEnable(true);
        return main;
    }

    private void startSearch() {
        if (searchRunning && searchPaused) {
            if (searchRunner != null) {
                searchRunner.resume();
                searchPaused = false;
                searchPauseButton.setText(getString("button.pause"));
            }
            return;
        }
        if (GeodeSearchRunner.isAnySearchActive()) {
            showError("error.searchBusy");
            return;
        }
        Long seed = parseSeed(searchSeedField.getText());
        if (seed == null) {
            return;
        }
        Integer threads = parseThreads(searchThreadCountField);
        if (threads == null) {
            return;
        }
        Integer geodes = parseAtLeastOne(searchGeodeField, "error.geodeThresholdInvalid");
        if (geodes == null) {
            return;
        }
        int budding = 1;
        if (!searchStructureCheck.isSelected()) {
            Integer parsed = parseAtLeastOne(searchBuddingField, "error.buddingThresholdInvalid");
            if (parsed == null) {
                return;
            }
            budding = parsed;
        }
        Integer radius = parseRadius(searchRadiusField);
        if (radius == null) {
            return;
        }
        int[] bounds = parseBounds(minXField, maxXField, minZField, maxZField, DEFAULT_MIN, DEFAULT_MAX);
        if (bounds == null) {
            return;
        }
        if (!confirmBlockBounds(bounds[0], bounds[1], bounds[2], bounds[3])) {
            return;
        }
        int minChunkX = GeodeSearchRunner.blockToChunk(bounds[0]);
        int maxChunkX = GeodeSearchRunner.blockToChunk(bounds[1]);
        int minChunkZ = GeodeSearchRunner.blockToChunk(bounds[2]);
        int maxChunkZ = GeodeSearchRunner.blockToChunk(bounds[3]);
        final long token = searchToken.incrementAndGet();
        searchRunning = true;
        searchPaused = false;
        setSearchInputsEnabled(false);
        searchResultArea.setText("");
        searchProgressBar.setIndeterminate(true);
        searchElapsedLabel.setText(getString("elapsedTime", formatTime(0)));
        searchRemainingLabel.setText(getString("remainingTime.calculating"));
        searchRunner = new GeodeSearchRunner();
        int mode = searchMode(searchStructureCheck, searchFastCheck);
        int version = searchVersionCombo.getSelectedIndex();
        if (!searchRunner.startSearch(seed, minChunkX, maxChunkX, minChunkZ, maxChunkZ, geodes, budding, radius, threads,
            version, mode, true, info -> updateSearchProgress(token, info), text -> showSearchText(token, text))) {
            searchRunning = false;
            setSearchInputsEnabled(true);
            showError("error.searchBusy");
        }
    }

    private void toggleSearchPause() {
        if (searchRunner == null || !searchRunning) {
            return;
        }
        if (searchPaused) {
            searchRunner.resume();
            searchPaused = false;
            searchPauseButton.setText(getString("button.pause"));
        } else {
            searchRunner.pause();
            searchPaused = true;
            searchPauseButton.setText(getString("button.resume"));
        }
    }

    private void stopSearch() {
        if (searchRunner != null) {
            searchRunner.stop();
        }
        searchRunning = false;
        searchPaused = false;
        setSearchInputsEnabled(true);
        searchProgressBar.setIndeterminate(false);
        searchRemainingLabel.setText(getString("remainingTime.stopped"));
    }

    private void updateSearchProgress(long token, GeodeSearchRunner.ProgressInfo info) {
        SwingUtilities.invokeLater(() -> {
            if (token != searchToken.get()) {
                return;
            }
            if (info.done()) {
                if (!searchRunning) {
                    return;
                }
                searchRunning = false;
                searchPaused = false;
                setSearchInputsEnabled(true);
                searchProgressBar.setIndeterminate(false);
                searchProgressBar.setValue(100);
                searchProgressBar.setString(getString("progress.complete", info.processed(), info.total()));
                searchElapsedLabel.setText(getString("elapsedTime", formatTime(info.elapsedMs())));
                searchRemainingLabel.setText(getString("remainingTime.completed"));
                return;
            }
            if (!searchRunning) {
                return;
            }
            searchProgressBar.setIndeterminate(false);
            searchProgressBar.setValue((int) Math.min(100, info.percentage()));
            String key = info.phase() == 2 ? "progress.stage2" : "progress.stage1";
            searchProgressBar.setString(getString(key, info.processed(), info.total(), info.percentage()));
            searchElapsedLabel.setText(getString("elapsedTime", formatTime(info.elapsedMs())));
            if (searchPaused) {
                searchRemainingLabel.setText(getString("remainingTime.paused"));
            } else if (info.remainingMs() > 0) {
                searchRemainingLabel.setText(getString("remainingTime", formatTime(info.remainingMs())));
            } else {
                searchRemainingLabel.setText(getString("remainingTime.calculating"));
            }
        });
    }

    private void showSearchText(long token, String text) {
        SwingUtilities.invokeLater(() -> {
            if (token != searchToken.get() || text == null) {
                return;
            }
            searchResultArea.setText(text);
            searchResultArea.setCaretPosition(searchResultArea.getDocument().getLength());
        });
    }

    private void startListSearch() {
        if (listRunning && listPaused) {
            if (listRunner != null) {
                listRunner.resume();
                listPaused = false;
                listPauseButton.setText(getString("button.pause"));
            }
            return;
        }
        if (GeodeSearchRunner.isAnySearchActive()) {
            showError("error.searchBusy");
            return;
        }
        if (selectedSeedFile == null) {
            showError("error.seedFileRequired");
            return;
        }
        final List<Long> seeds;
        try {
            seeds = readSeedList(selectedSeedFile);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, getString("error.seedFileReadFailed", ex.getMessage()),
                getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (seeds.isEmpty()) {
            showError("error.seedFileEmpty");
            return;
        }
        Integer threads = parseThreads(listThreadCountField);
        if (threads == null) {
            return;
        }
        Integer geodes = parseAtLeastOne(listGeodeField, "error.geodeThresholdInvalid");
        if (geodes == null) {
            return;
        }
        int budding = 1;
        if (!listStructureCheck.isSelected()) {
            Integer parsed = parseAtLeastOne(listBuddingField, "error.buddingThresholdInvalid");
            if (parsed == null) {
                return;
            }
            budding = parsed;
        }
        Integer radius = parseRadius(listRadiusField);
        if (radius == null) {
            return;
        }
        int[] bounds = parseBounds(listMinXField, listMaxXField, listMinZField, listMaxZField, DEFAULT_LIST_MIN, DEFAULT_LIST_MAX);
        if (bounds == null || !confirmBlockBounds(bounds[0], bounds[1], bounds[2], bounds[3])) {
            return;
        }
        final long token = listToken.incrementAndGet();
        final int finalBudding = budding;
        final int finalThreads = threads;
        final int finalGeodes = geodes;
        final int finalRadius = radius;
        final int version = listVersionCombo.getSelectedIndex();
        final int mode = searchMode(listStructureCheck, listFastCheck);
        listRunning = true;
        listPaused = false;
        setListInputsEnabled(false);
        listResultArea.setText("");
        listProgressBar.setIndeterminate(false);
        listProgressBar.setMaximum(seeds.size());
        listProgressBar.setValue(0);
        listProgressBar.setString(getString("progress.seedsDone", 0, seeds.size()));
        listElapsedLabel.setText(getString("elapsedTime", formatTime(0)));
        listRemainingLabel.setText(getString("remainingTime.calculating"));
        listPausedAccum = 0;
        listPauseStarted = 0;
        final long started = System.currentTimeMillis();
        final int minChunkX = GeodeSearchRunner.blockToChunk(bounds[0]);
        final int maxChunkX = GeodeSearchRunner.blockToChunk(bounds[1]);
        final int minChunkZ = GeodeSearchRunner.blockToChunk(bounds[2]);
        final int maxChunkZ = GeodeSearchRunner.blockToChunk(bounds[3]);
        new Thread(() -> runSeedList(token, seeds, new int[] {minChunkX, maxChunkX, minChunkZ, maxChunkZ}, finalGeodes,
            finalBudding, finalRadius, finalThreads, version, mode, started),
            "geodefinder-list").start();
    }

    private void runSeedList(long token, List<Long> seeds, int[] bounds, int geodes, int budding, int radius,
                             int threads, int version, int mode, long started) {
        for (int i = 0; i < seeds.size(); i++) {
            if (!listRunning || token != listToken.get()) {
                return;
            }
            long seed = seeds.get(i);
            int index = i + 1;
            listRunner = new GeodeSearchRunner();
            Consumer<GeodeSearchRunner.ProgressInfo> progress = info -> {
                if (info.done() || token != listToken.get()) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    if (token != listToken.get() || !listRunning) {
                        return;
                    }
                    String key = info.phase() == 2 ? "currentSeed.stage2" : "currentSeed.stage1";
                    listSeedProgressLabel.setText(getString(key, index, seeds.size(), info.processed(), info.total(), info.percentage()));
                    listElapsedLabel.setText(getString("elapsedTime", formatTime(listElapsedMs(started))));
                    if (listPaused) {
                        listRemainingLabel.setText(getString("remainingTime.paused"));
                    } else if (info.remainingMs() > 0) {
                        listRemainingLabel.setText(getString("remainingTime", formatTime(info.remainingMs())));
                    }
                });
            };
            StringBuilder received = new StringBuilder();
            boolean startedOk = listRunner.runSearchBlocking(seed, bounds[0], bounds[1], bounds[2], bounds[3], geodes,
                budding, radius, threads, version, mode, progress, received::append);
            if (!startedOk) {
                SwingUtilities.invokeLater(() -> showError("error.searchBusy"));
                break;
            }
            String text = received.toString();
            if (!text.isBlank() && token == listToken.get()) {
                SwingUtilities.invokeLater(() -> {
                    if (token != listToken.get()) {
                        return;
                    }
                    listResultArea.append(seed + "\n");
                    listResultArea.append(text.endsWith("\n") ? text : text + "\n");
                    listResultArea.setCaretPosition(listResultArea.getDocument().getLength());
                });
            }
            int done = i + 1;
            SwingUtilities.invokeLater(() -> {
                if (token != listToken.get()) {
                    return;
                }
                listProgressBar.setValue(Math.min(done, seeds.size()));
                listProgressBar.setString(getString("progress.seedsDone", Math.min(done, seeds.size()), seeds.size()));
            });
            if (!listRunning) {
                return;
            }
        }
        SwingUtilities.invokeLater(() -> {
            if (token != listToken.get() || !listRunning) {
                return;
            }
            listRunning = false;
            listPaused = false;
            setListInputsEnabled(true);
            listProgressBar.setValue(seeds.size());
            listProgressBar.setString(getString("progress.seedsDone", seeds.size(), seeds.size()));
            listSeedProgressLabel.setText(getString("currentSeed.complete"));
            listElapsedLabel.setText(getString("elapsedTime", formatTime(listElapsedMs(started))));
            listRemainingLabel.setText(getString("remainingTime.completed"));
        });
    }

    private void toggleListPause() {
        if (listRunner == null || !listRunning) {
            return;
        }
        if (listPaused) {
            if (listPauseStarted > 0) {
                listPausedAccum += System.currentTimeMillis() - listPauseStarted;
                listPauseStarted = 0;
            }
            listRunner.resume();
            listPaused = false;
            listPauseButton.setText(getString("button.pause"));
        } else {
            listPauseStarted = System.currentTimeMillis();
            listRunner.pause();
            listPaused = true;
            listPauseButton.setText(getString("button.resume"));
        }
    }

    private long listElapsedMs(long started) {
        long paused = listPausedAccum;
        if (listPauseStarted > 0) {
            paused += System.currentTimeMillis() - listPauseStarted;
        }
        return Math.max(0, System.currentTimeMillis() - started - paused);
    }

    private void stopListSearch() {
        if (listRunner != null) {
            listRunner.stop();
        }
        listRunning = false;
        listPaused = false;
        setListInputsEnabled(true);
        listRemainingLabel.setText(getString("remainingTime.stopped"));
    }

    private void setSearchInputsEnabled(boolean enabled) {
        searchStartButton.setEnabled(enabled);
        searchPauseButton.setEnabled(!enabled);
        searchPauseButton.setText(getString("button.pause"));
        searchStopButton.setEnabled(!enabled);
        searchResetButton.setEnabled(enabled);
        searchWorldBorderButton.setEnabled(enabled);
        searchSeedField.setEnabled(enabled);
        searchThreadCountField.setEnabled(enabled);
        searchVersionCombo.setEnabled(enabled);
        searchGeodeField.setEnabled(enabled);
        searchRadiusField.setEnabled(enabled);
        searchStructureCheck.setEnabled(enabled);
        searchFastCheck.setEnabled(enabled);
        searchBuddingField.setEnabled(enabled && !searchStructureCheck.isSelected());
        languageComboBox.setEnabled(enabled);
        applyBoundEnable(enabled);
        if (enabled) {
            searchPaused = false;
        }
    }

    private void setListInputsEnabled(boolean enabled) {
        listStartButton.setEnabled(enabled);
        listPauseButton.setEnabled(!enabled);
        listPauseButton.setText(getString("button.pause"));
        listStopButton.setEnabled(!enabled);
        listResetButton.setEnabled(enabled);
        listFileButton.setEnabled(enabled);
        listThreadCountField.setEnabled(enabled);
        listVersionCombo.setEnabled(enabled);
        listGeodeField.setEnabled(enabled);
        listRadiusField.setEnabled(enabled);
        listStructureCheck.setEnabled(enabled);
        listFastCheck.setEnabled(enabled);
        listBuddingField.setEnabled(enabled && !listStructureCheck.isSelected());
        applyListBoundEnable(enabled);
        if (enabled) {
            listPaused = false;
        }
    }

    private void applySearchBounds(int minX, int maxX, int minZ, int maxZ) {
        squareSideCheckBox.setSelected(true);
        squareSideUpdating = true;
        squareSideField.setText(String.valueOf((long) maxX - minX + 1));
        squareSideUpdating = false;
        minXField.setText(String.valueOf(minX));
        maxXField.setText(String.valueOf(maxX));
        minZField.setText(String.valueOf(minZ));
        maxZField.setText(String.valueOf(maxZ));
        applyBoundEnable(!searchRunning);
        applySquareSide(false);
    }

    private void applyListBounds(int minX, int maxX, int minZ, int maxZ) {
        listSquareSideCheckBox.setSelected(true);
        listSquareSideUpdating = true;
        listSquareSideField.setText(String.valueOf((long) maxX - minX + 1));
        listSquareSideUpdating = false;
        listMinXField.setText(String.valueOf(minX));
        listMaxXField.setText(String.valueOf(maxX));
        listMinZField.setText(String.valueOf(minZ));
        listMaxZField.setText(String.valueOf(maxZ));
        applyListBoundEnable(!listRunning);
        applySquareSide(true);
    }

    private void applySquareSide(boolean list) {
        boolean updating = list ? listSquareSideUpdating : squareSideUpdating;
        JCheckBox check = list ? listSquareSideCheckBox : squareSideCheckBox;
        JTextField sideField = list ? listSquareSideField : squareSideField;
        if (updating || check == null || !check.isSelected() || sideField == null) {
            return;
        }
        try {
            long side = Long.parseLong(sideField.getText().trim());
            if (side < 1) {
                return;
            }
            long min = -side / 2;
            long max = min + side - 1;
            if (min < Integer.MIN_VALUE || max > Integer.MAX_VALUE) {
                return;
            }
            if (list) {
                listSquareSideUpdating = true;
            } else {
                squareSideUpdating = true;
            }
            (list ? listMinXField : minXField).setText(String.valueOf((int) min));
            (list ? listMaxXField : maxXField).setText(String.valueOf((int) max));
            (list ? listMinZField : minZField).setText(String.valueOf((int) min));
            (list ? listMaxZField : maxZField).setText(String.valueOf((int) max));
        } catch (NumberFormatException ignored) {
            return;
        } finally {
            if (list) {
                listSquareSideUpdating = false;
            } else {
                squareSideUpdating = false;
            }
        }
    }

    private void applyBoundEnable(boolean allowEdit) {
        boolean square = squareSideCheckBox.isSelected();
        squareSideCheckBox.setEnabled(allowEdit);
        squareSideField.setEnabled(allowEdit && square);
        boolean bounds = allowEdit && !square;
        minXField.setEnabled(bounds);
        maxXField.setEnabled(bounds);
        minZField.setEnabled(bounds);
        maxZField.setEnabled(bounds);
    }

    private void applyListBoundEnable(boolean allowEdit) {
        boolean square = listSquareSideCheckBox.isSelected();
        listSquareSideCheckBox.setEnabled(allowEdit);
        listSquareSideField.setEnabled(allowEdit && square);
        boolean bounds = allowEdit && !square;
        listMinXField.setEnabled(bounds);
        listMaxXField.setEnabled(bounds);
        listMinZField.setEnabled(bounds);
        listMaxZField.setEnabled(bounds);
    }

    private Long parseSeed(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            showError("error.seedRequired");
            return null;
        }
        try {
            double asDouble = Double.parseDouble(trimmed);
            if (asDouble != Math.floor(asDouble)) {
                showError("error.seedMustBeInteger");
                return null;
            }
        } catch (NumberFormatException ex) {
            showError("error.seedFormatError");
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            showError("error.seedOutOfRange");
            return null;
        }
    }

    private Integer parseThreads(JTextField field) {
        try {
            double value = Double.parseDouble(field.getText().trim());
            if (value != Math.floor(value)) {
                showError("error.threadCountMustBeInteger");
                return null;
            }
            int threads = (int) value;
            if (threads < 1) {
                showError("error.threadCountRequired");
                return null;
            }
            int cpu = Runtime.getRuntime().availableProcessors();
            if (threads > cpu) {
                int choice = JOptionPane.showConfirmDialog(this, getString("error.threadCountExceedsCPU", cpu, cpu),
                    getString("prompt.adjustThreadCount"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    return null;
                }
                field.setText(String.valueOf(cpu));
                return cpu;
            }
            return threads;
        } catch (NumberFormatException ex) {
            showError("error.threadCountFormatError");
            return null;
        }
    }

    private Integer parseAtLeastOne(JTextField field, String errorKey) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < 1) {
                showError(errorKey);
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            showError(errorKey);
            return null;
        }
    }

    private Integer parseRadius(JTextField field) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < 0 || value > 64) {
                showError("error.loadedRadiusInvalid");
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            showError("error.loadedRadiusInvalid");
            return null;
        }
    }

    /** @return block minX, maxX, minZ, maxZ, or null */
    private int[] parseBounds(JTextField minX, JTextField maxX, JTextField minZ, JTextField maxZ, int fallbackMin, int fallbackMax) {
        Integer x0 = parseBlock(minX, "error.minXMustBeInteger", "error.minXFormatError", fallbackMin);
        Integer x1 = parseBlock(maxX, "error.maxXMustBeInteger", "error.maxXFormatError", fallbackMax);
        Integer z0 = parseBlock(minZ, "error.minZMustBeInteger", "error.minZFormatError", fallbackMin);
        Integer z1 = parseBlock(maxZ, "error.maxZMustBeInteger", "error.maxZFormatError", fallbackMax);
        if (x0 == null || x1 == null || z0 == null || z1 == null) {
            return null;
        }
        if (x0 > x1) {
            showError("error.minXGreaterThanMaxX");
            return null;
        }
        if (z0 > z1) {
            showError("error.minZGreaterThanMaxZ");
            return null;
        }
        long width = (long) x1 - x0 + 1;
        long height = (long) z1 - z0 + 1;
        if (width <= 0 || height <= 0 || width > GeodeSearchRunner.MAX_SEARCH_AREA_BLOCKS / height) {
            showError("error.searchAreaTooLarge");
            return null;
        }
        return new int[] {x0, x1, z0, z1};
    }

    private Integer parseBlock(JTextField field, String fractionKey, String formatKey, int fallback) {
        try {
            double value = Double.parseDouble(field.getText().trim());
            if (value != Math.floor(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                showError(fractionKey);
                field.setText(String.valueOf(fallback));
                return null;
            }
            return (int) value;
        } catch (NumberFormatException ex) {
            showError(formatKey);
            field.setText(String.valueOf(fallback));
            return null;
        }
    }

    private boolean confirmBlockBounds(int minX, int maxX, int minZ, int maxZ) {
        boolean outside = minX < BOUNDARY_MIN || maxX > BOUNDARY_MAX || minZ < BOUNDARY_MIN || maxZ > BOUNDARY_MAX;
        if (!outside) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(this, getString("error.outOfBounds"), getString("prompt.warning"),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    private static int searchMode(JCheckBox structureOnly, JCheckBox fast) {
        if (structureOnly.isSelected()) {
            return GeodeSearchRunner.MODE_STRUCTURE;
        }
        if (fast.isSelected()) {
            return GeodeSearchRunner.MODE_FAST;
        }
        return GeodeSearchRunner.MODE_FULL;
    }

    private void wireModeChecks(JCheckBox structureOnly, JCheckBox fast, JTextField budding) {
        structureOnly.addActionListener(e -> {
            if (structureOnly.isSelected()) {
                fast.setSelected(false);
            }
            budding.setEnabled(!structureOnly.isSelected());
        });
        fast.addActionListener(e -> {
            if (fast.isSelected()) {
                structureOnly.setSelected(false);
                budding.setEnabled(true);
            }
        });
    }

    private void chooseSeedFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(getString("dialog.selectSeedFile"));
        chooser.setFileFilter(new FileNameExtensionFilter(getString("dialog.textFiles"), "txt"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedSeedFile = chooser.getSelectedFile();
            listFileLabel.setText(selectedSeedFile.getName());
        }
    }

    private List<Long> readSeedList(File file) throws IOException {
        List<Long> seeds = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    seeds.add(Long.parseLong(line));
                } catch (NumberFormatException ex) {
                    System.err.println("跳过无效的种子行: " + line);
                }
            }
        }
        return seeds;
    }

    private void exportText(String text, String defaultName) {
        if (text == null || text.isBlank()) {
            JOptionPane.showMessageDialog(this, getString("error.noResultsToExport"), getString("prompt.information"),
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(getString("dialog.exportResults"));
        chooser.setFileFilter(new FileNameExtensionFilter(getString("dialog.textFiles"), "txt"));
        chooser.setSelectedFile(new File(defaultName));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(chooser.getSelectedFile()))) {
                writer.print(text);
                JOptionPane.showMessageDialog(this, getString("success.export"), getString("prompt.success"),
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, getString("error.exportFailed", ex.getMessage()),
                    getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportSeedList() {
        List<Long> seeds = new ArrayList<>();
        for (String line : listResultArea.getText().split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("/tp ")) {
                continue;
            }
            try {
                long seed = Long.parseLong(line);
                if (!seeds.contains(seed)) {
                    seeds.add(seed);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (seeds.isEmpty()) {
            JOptionPane.showMessageDialog(this, getString("error.noSeedsFound"), getString("prompt.information"),
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(getString("dialog.exportSeedList"));
        chooser.setFileFilter(new FileNameExtensionFilter(getString("dialog.textFiles"), "txt"));
        chooser.setSelectedFile(new File(getString("file.seedList")));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(chooser.getSelectedFile()))) {
                for (Long seed : seeds) {
                    writer.println(seed);
                }
                JOptionPane.showMessageDialog(this, getString("success.exportSeeds", seeds.size()),
                    getString("prompt.success"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, getString("error.exportFailed", ex.getMessage()),
                    getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private String sortLines(String text) {
        List<HitLine> hits = new ArrayList<>();
        for (String line : text.split("\n")) {
            HitLine hit = HitLine.parse(line.trim());
            if (hit != null) {
                hits.add(hit);
            }
        }
        hits.sort(HitLine.BY_METRIC);
        StringBuilder sb = new StringBuilder();
        for (HitLine hit : hits) {
            sb.append(hit.line).append('\n');
        }
        return sb.toString();
    }

    private String sortSeedGroups(boolean byMetric) {
        Map<Long, List<HitLine>> groups = new HashMap<>();
        List<Long> order = new ArrayList<>();
        Long current = null;
        for (String raw : listResultArea.getText().split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            HitLine hit = HitLine.parse(line);
            if (hit != null && current != null) {
                groups.get(current).add(hit);
                continue;
            }
            try {
                current = Long.parseLong(line);
                groups.computeIfAbsent(current, key -> new ArrayList<>());
                if (!order.contains(current)) {
                    order.add(current);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        List<Long> seeds = new ArrayList<>(order);
        if (byMetric) {
            seeds.sort((a, b) -> Integer.compare(maxMetric(groups.get(b)), maxMetric(groups.get(a))));
        } else {
            seeds.sort(Comparator.comparingLong(seed -> minDistance(groups.get(seed))));
        }
        StringBuilder sb = new StringBuilder();
        for (Long seed : seeds) {
            List<HitLine> hits = groups.get(seed);
            if (hits == null || hits.isEmpty()) {
                continue;
            }
            hits.sort(HitLine.BY_METRIC);
            sb.append(seed).append('\n');
            for (HitLine hit : hits) {
                sb.append(hit.line).append('\n');
            }
        }
        return sb.toString();
    }

    private static int maxMetric(List<HitLine> hits) {
        int max = Integer.MIN_VALUE;
        if (hits == null) {
            return 0;
        }
        for (HitLine hit : hits) {
            max = Math.max(max, hit.metric);
        }
        return max == Integer.MIN_VALUE ? 0 : max;
    }

    private static long minDistance(List<HitLine> hits) {
        long min = Long.MAX_VALUE;
        if (hits == null) {
            return min;
        }
        for (HitLine hit : hits) {
            min = Math.min(min, hit.distanceSquared);
        }
        return min;
    }

    private void changeLanguage() {
        if (searchRunning || listRunning || languageComboBox == null) {
            if (languageComboBox != null) {
                languageComboBox.setSelectedIndex("zh".equals(currentLocale.getLanguage()) ? 0 : 1);
            }
            return;
        }
        int index = languageComboBox.getSelectedIndex();
        Locale next = index == 0 ? new Locale("zh", "CN") : new Locale("en", "US");
        if (next.equals(currentLocale)) {
            return;
        }
        try {
            currentLocale = next;
            messages = ResourceBundle.getBundle("messages", currentLocale);
        } catch (RuntimeException ex) {
            return;
        }
        setTitle(getString("window.title"));
        tabbedPane.setTitleAt(0, getString("tab.singleSeedSearch"));
        tabbedPane.setTitleAt(1, getString("tab.listSearch"));
        applyI18n(getContentPane());
        searchCreditLabel.setText(buildCreditText());
        listCreditLabel.setText(buildCreditText());
        if (!searchRunning) {
            searchProgressBar.setString(getString("progress.format", 0, 0, 0.0));
            searchElapsedLabel.setText(getString("elapsedTime", formatTime(0)));
            searchRemainingLabel.setText(getString("remainingTime.calculating"));
        }
        if (selectedSeedFile == null) {
            listFileLabel.setText(getString("label.noFileSelected"));
        }
        listSeedProgressLabel.setText(getString("currentSeed.default"));
        listElapsedLabel.setText(getString("elapsedTime", formatTime(0)));
        listRemainingLabel.setText(getString("remainingTime.calculating"));
        listProgressBar.setString(getString("progress.seedsDone", 0, 0));
    }

    private void applyI18n(Component component) {
        if (component instanceof JComponent jc) {
            Object key = jc.getClientProperty("i18n");
            if (key instanceof String textKey) {
                if (jc instanceof AbstractButton button) {
                    button.setText(getString(textKey));
                } else if (jc instanceof JLabel label) {
                    label.setText(getString(textKey));
                }
            }
            Object borderKey = jc.getClientProperty("i18n.border");
            if (borderKey instanceof String titled) {
                jc.setBorder(BorderFactory.createTitledBorder(getString(titled)));
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyI18n(child);
            }
        }
    }

    private String buildCreditText() {
        StringBuilder hints = new StringBuilder();
        hints.append(getString("hint.fullSearch")).append("<br>");
        hints.append(getString("hint.structureOnly")).append("<br>");
        hints.append(getString("hint.fastSearch")).append("<br>");
        hints.append(getString("hint.outputHint"));
        return getString("credit.text", hints.toString());
    }

    private void showError(String key) {
        JOptionPane.showMessageDialog(this, getString(key), getString("prompt.error"), JOptionPane.ERROR_MESSAGE);
    }

    private String getString(String key) {
        try {
            return messages.getString(key);
        } catch (RuntimeException ex) {
            return key;
        }
    }

    private String getString(String key, Object... args) {
        try {
            return MessageFormat.format(messages.getString(key), args);
        } catch (RuntimeException ex) {
            return key;
        }
    }

    private String formatTime(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return getString("time.format", days, hours, minutes, seconds);
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String key, JComponent field) {
        JLabel label = new JLabel(getString(key));
        label.setFont(getLoadedFont());
        label.putClientProperty("i18n", key);
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(label, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        field.setFont(getLoadedFont());
        panel.add(field, gbc);
        gbc.gridx = 0;
        gbc.gridwidth = 2;
    }

    private void addSideBySide(JPanel panel, GridBagConstraints gbc, int row, JComponent left, JComponent right) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(left, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        right.setFont(getLoadedFont());
        panel.add(right, gbc);
        gbc.gridx = 0;
        gbc.gridwidth = 2;
    }

    private void addFullWidth(JPanel panel, GridBagConstraints gbc, int row, JComponent component) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(component, gbc);
    }

    private JButton taggedButton(String key) {
        JButton button = new JButton(getString(key));
        button.setFont(getLoadedFont());
        button.putClientProperty("i18n", key);
        return button;
    }

    private JCheckBox taggedCheck(String key, boolean selected) {
        JCheckBox check = new JCheckBox(getString(key), selected);
        check.setFont(getLoadedFont());
        check.putClientProperty("i18n", key);
        return check;
    }

    private static GridBagConstraints formConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        return gbc;
    }

    private static javax.swing.event.DocumentListener simpleListener(Runnable action) {
        return new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        };
    }

    private void setWindowIcon() {
        try {
            java.net.URL iconUrl = getClass().getResource("/icon.png");
            if (iconUrl != null) {
                Image image = new javax.swing.ImageIcon(iconUrl).getImage();
                setIconImage(image);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void setChineseFont() {
        try (java.io.InputStream fontStream = getClass().getResourceAsStream("/font.ttf")) {
            if (fontStream == null) {
                return;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, fontStream);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            loadedFont = font.deriveFont(Font.PLAIN, 12f);
            UIManager.put("Label.font", loadedFont);
            UIManager.put("Button.font", loadedFont);
            UIManager.put("TextField.font", loadedFont);
            UIManager.put("TextArea.font", loadedFont);
            UIManager.put("ComboBox.font", loadedFont);
            UIManager.put("TabbedPane.font", loadedFont);
            UIManager.put("ProgressBar.font", loadedFont);
            UIManager.put("ToolTip.font", loadedFont);
            UIManager.put("Menu.font", loadedFont);
            UIManager.put("MenuItem.font", loadedFont);
            UIManager.put("CheckBox.font", loadedFont);
            UIManager.put("RadioButton.font", loadedFont);
            UIManager.put("List.font", loadedFont);
            UIManager.put("Table.font", loadedFont);
            UIManager.put("Tree.font", loadedFont);
        } catch (FontFormatException | IOException ignored) {
        }
    }

    private Font getLoadedFont() {
        return loadedFont != null ? loadedFont : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new GeodeFinderFrame().setVisible(true);
        });
    }

    private static final class HitLine {
        static final Comparator<HitLine> BY_METRIC = Comparator
            .comparingInt((HitLine hit) -> hit.metric).reversed()
            .thenComparingLong(hit -> hit.distanceSquared);

        final String line;
        final int metric;
        final long distanceSquared;

        private HitLine(String line, int metric, long distanceSquared) {
            this.line = line;
            this.metric = metric;
            this.distanceSquared = distanceSquared;
        }

        static HitLine parse(String line) {
            if (!line.startsWith("/tp ")) {
                return null;
            }
            String[] parts = line.split("\\s+");
            if (parts.length < 4) {
                return null;
            }
            try {
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[3]);
                int budding = Integer.MIN_VALUE;
                int geodes = Integer.MIN_VALUE;
                for (String part : parts) {
                    if (part.startsWith("budding=")) {
                        budding = Integer.parseInt(part.substring("budding=".length()));
                    } else if (part.startsWith("geodes=")) {
                        geodes = Integer.parseInt(part.substring("geodes=".length()));
                    }
                }
                if (geodes == Integer.MIN_VALUE) {
                    return null;
                }
                int metric = budding != Integer.MIN_VALUE ? budding : geodes;
                return new HitLine(line, metric, (long) x * x + (long) z * z);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
