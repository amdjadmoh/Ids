package cic.cs.unb.ca.flow.ui;

import cic.cs.unb.ca.Sys;
import cic.cs.unb.ca.flow.FlowMgr;
import cic.cs.unb.ca.guava.Event.FlowVisualEvent;
import cic.cs.unb.ca.guava.GuavaMgr;
import cic.cs.unb.ca.jnetpcap.BasicFlow;
import cic.cs.unb.ca.jnetpcap.FlowFeature;
import cic.cs.unb.ca.jnetpcap.PcapIfWrapper;
import cic.cs.unb.ca.jnetpcap.worker.LoadPcapInterfaceWorker;
import cic.cs.unb.ca.jnetpcap.worker.TrafficFlowWorker;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jnetpcap.Pcap;
import org.jnetpcap.PcapIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cic.cs.unb.ca.jnetpcap.worker.InsertCsvRow;
import swing.common.InsertTableRow;
import swing.common.JTable2CSVWorker;
import swing.common.TextFileFilter;
import java.net.*;
import java.util.Enumeration;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import java.io.DataOutputStream;



public  class FlowMonitorPane extends JPanel {
    protected static final Logger logger = LoggerFactory.getLogger(FlowMonitorPane.class);
    private int count = 0;
    private Socket s;  
    private DataOutputStream dout;
    private JTable flowTable;
    private DefaultTableModel defaultTableModel;
    public JList<PcapIfWrapper> list;
    private DefaultListModel<PcapIfWrapper> listModel;
    private JLabel lblStatus;
    private JLabel lblFlowCnt;

    private TrafficFlowWorker mWorker;

    public JButton btnScan;
    public JButton btnScanning;
    private ButtonGroup btnGroup;
    private JLabel explanatoryLabel;
    private String rootPath;
    private File lastSave;
    private JFileChooser fileChooser;

    private ExecutorService csvWriterThread;
    private String currentSaveDir;
    private String currentSaveFileName;
    public Timer timer;
    private TimerTask task;


    public FlowMonitorPane() throws SocketException, UnknownHostException,MalformedURLException,IOException{
        init();
        setLayout(new BorderLayout());
        add(initCenterPane());
        //open connection to server
        try{
        String modelHost = System.getenv("MODEL_HOST");
        if (modelHost == null || modelHost.trim().isEmpty()) {
            modelHost = "127.0.0.1";
        }
        s=new Socket(modelHost,5000);
        dout=new DataOutputStream(s.getOutputStream());
        }catch(Exception e){System.out.println(e);}
        //
        //close connection to server when shutting down
        Runtime.getRuntime().addShutdownHook(new Thread(){public void run(){
            try {
            	dout.writeUTF("exit");
                dout.close();  
                s.close();
            } catch (IOException e) { e.printStackTrace(); }
        }});     
        //
    }

    private void init() {
        csvWriterThread = Executors.newSingleThreadExecutor();
    }

    public void destory() {
        csvWriterThread.shutdown();
    }

    private JPanel initCenterPane() throws SocketException, UnknownHostException,IOException{
        initTablePane();
        initNWifsPane();
        JPanel pane = new JPanel();
        pane.setLayout(new GridBagLayout());
        pane.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        
        pane.setBackground(new Color(85, 98, 132));
        rootPath = System.getProperty("user.dir");
        GridBagConstraints c = new GridBagConstraints();
       
        // logo
        c.gridx = c.gridy =0;
        c.weightx = c.weighty =  1.0;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        pane.add(logo(),c);

        // click to scan network button (init button)
        c.anchor = GridBagConstraints.CENTER;
        pane.add(scanButton(),c);
        
        // Scanning button
        c.anchor = GridBagConstraints.CENTER;
        pane.add(scanningButton(),c);

        // text label
        c.anchor = GridBagConstraints.PAGE_END;
        c.insets = new Insets(0,0,33,0);
        pane.add(explanatoryLabel(),c);
        return pane;
    }

    private JLabel logo() throws IOException{
        JLabel label = new JLabel();
        label.setForeground(Color.white);
        BufferedImage labelIcon = ImageIO.read(new File(rootPath+"/src/main/resources/esi_ids.png"));
        label.setIcon(new ImageIcon(labelIcon));
        return label;
    }

    private JButton scanButton() throws IOException{
        BufferedImage buttonIcon = ImageIO.read(new File(rootPath+"/src/main/resources/scan_network.png"));
        btnScan = new JButton(new ImageIcon(buttonIcon));
        btnScan.setContentAreaFilled(false);
        btnScan.setBorder(BorderFactory.createEmptyBorder());
        btnScan.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnScan.setHorizontalAlignment(JButton.CENTER);
        btnScan.setVerticalAlignment(JButton.CENTER);
        btnScan.setVisible(true);
        
        btnScan.addActionListener(actionEvent ->{
            btnScanning.setVisible(true);
            btnScan.setVisible(false);
            try {
                startTrafficFlow();
            }catch (SocketException e){
                e.printStackTrace();
            }catch (UnknownHostException e){
                e.printStackTrace();
            }
        } );
    
        return btnScan;
    }

    private JButton scanningButton() throws IOException{
        BufferedImage buttonIcon2 = ImageIO.read(new File(rootPath+"/src/main/resources/scanning.png"));
        btnScanning = new JButton(new ImageIcon(buttonIcon2));
        btnScanning.setContentAreaFilled(false);
        btnScanning.setBorder(BorderFactory.createEmptyBorder());
        btnScanning.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnScanning.setHorizontalAlignment(JButton.CENTER);
        btnScanning.setVerticalAlignment(JButton.CENTER);
        btnScanning.setVisible(false);
        btnScanning.addActionListener(actionEvent ->{
            btnScanning.setVisible(false);              
            btnScan.setVisible(true);
            stopTrafficFlow();
         } );
        return btnScanning;
    }

    private JLabel explanatoryLabel() throws IOException{
        BufferedImage labelIcon2 = ImageIO.read(new File(rootPath+"/src/main/resources/scan_text1.png"));
        explanatoryLabel = new JLabel();
        explanatoryLabel.setIcon(new ImageIcon(labelIcon2));
        return explanatoryLabel;
    }

    private void initTablePane() throws SocketException, UnknownHostException{
        String[] arrayHeader = StringUtils.split(FlowFeature.getHeader(), ",");
        defaultTableModel = new DefaultTableModel(arrayHeader,0);
    }

    private void initTableBtnPane() throws SocketException, UnknownHostException{
        fileChooser = new JFileChooser(new File(FlowMgr.getInstance().getmDataPath()));
        TextFileFilter csvChooserFilter = new TextFileFilter("csv file (*.csv)", new String[]{"csv"});
        fileChooser.setFileFilter(csvChooserFilter);
    }


    private void initNWifsPane() throws SocketException, UnknownHostException {
        loadPcapIfs();
        initNWifsListPane();
    }
    
    private void initNWifsListPane() {
        listModel = new DefaultListModel<>();
        listModel.addElement(new PcapIfWrapper("Click Load button to load network interfaces"));
        list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(1);
    }

    private void loadPcapIfs() {
        LoadPcapInterfaceWorker task = new LoadPcapInterfaceWorker();
        task.addPropertyChangeListener(event -> {
            if ("state".equals(event.getPropertyName())) {
                LoadPcapInterfaceWorker task1 = (LoadPcapInterfaceWorker) event.getSource();
                switch (task1.getState()) {
                    case STARTED:
                        break;
                    case DONE:
                        try {
                            java.util.List<PcapIf> ifs = task1.get();
                            List<PcapIfWrapper> pcapiflist = PcapIfWrapper.fromPcapIf(ifs);
                            listModel.removeAllElements();
                            for(PcapIfWrapper pcapif :pcapiflist) {
                                listModel.addElement(pcapif);
                            }
                        } catch (InterruptedException | ExecutionException e) {
                            logger.debug(e.getMessage());
                        }
                        break;
                }
            }
        });

        task.execute();

    }

    public void startTrafficFlow() throws SocketException,UnknownHostException {
        list = new JList<>(listModel);
        Object o=null;
        Process p;
        String ifName = resolveInterfaceName();
        if (ifName == null || ifName.isEmpty()) {
            logger.error("No valid network interface found. Set CICFLOW_IFACE env var or provide ./interface file.");
            return;
        }
        logger.info("Using interface: {}", ifName);
        if (mWorker != null && !mWorker.isCancelled()) {
            return;
        }

        mWorker = new TrafficFlowWorker(ifName);
        
        mWorker.addPropertyChangeListener(event -> {
            TrafficFlowWorker task = (TrafficFlowWorker) event.getSource();
            if (TrafficFlowWorker.PROPERTY_FLOW.equalsIgnoreCase(event.getPropertyName())) {
                insertFlow((BasicFlow) event.getNewValue());
            }
        });
        
        mWorker.execute();
        String path = FlowMgr.getInstance().getAutoSaveFile();
        File saveFile = new File(path);
        File saveParent = saveFile.getParentFile();
        if (saveParent != null && !saveParent.exists()) {
            saveParent.mkdirs();
        }
        currentSaveDir = (saveParent == null) ? FlowMgr.getInstance().getSavePath() : saveParent.getPath();
        currentSaveFileName = saveFile.getName();
        logger.info("path:{}", path);
    }

    private String normalizeInterfaceName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replace("\n", "").replace("\r", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private String readInterfaceFromFile() {
        try (BufferedReader br = new BufferedReader(new FileReader(new File("./interface")))) {
            String line;
            String lastNonEmpty = null;
            while ((line = br.readLine()) != null) {
                String normalized = normalizeInterfaceName(line);
                if (normalized != null) {
                    lastNonEmpty = normalized;
                }
            }
            return lastNonEmpty;
        } catch (Exception e) {
            logger.warn("./interface not available, using auto-detection");
            return null;
        }
    }

    private boolean isValidInterfaceName(String interfaceName) {
        String name = normalizeInterfaceName(interfaceName);
        if (name == null) {
            return false;
        }

        StringBuilder errbuf = new StringBuilder();
        List<PcapIf> ifs = new ArrayList<>();
        if (Pcap.findAllDevs(ifs, errbuf) != Pcap.OK) {
            logger.warn("Unable to list network interfaces: {}", errbuf.toString());
            return false;
        }

        for (PcapIf pcapIf : ifs) {
            if (name.equals(pcapIf.getName())) {
                return true;
            }
        }
        return false;
    }

    private String findFallbackInterface() {
        StringBuilder errbuf = new StringBuilder();
        List<PcapIf> ifs = new ArrayList<>();
        if (Pcap.findAllDevs(ifs, errbuf) != Pcap.OK) {
            logger.warn("Unable to auto-detect interface: {}", errbuf.toString());
            return null;
        }

        for (PcapIf pcapIf : ifs) {
            String name = pcapIf.getName();
            String lowerName = name.toLowerCase();
            if (!lowerName.contains("loopback") && !lowerName.startsWith("lo")) {
                return name;
            }
        }

        if (!ifs.isEmpty()) {
            return ifs.get(0).getName();
        }
        return null;
    }

    private String resolveInterfaceName() {
        String envInterface = normalizeInterfaceName(System.getenv("CICFLOW_IFACE"));
        if (isValidInterfaceName(envInterface)) {
            return envInterface;
        }

        String fileInterface = normalizeInterfaceName(readInterfaceFromFile());
        if (isValidInterfaceName(fileInterface)) {
            return fileInterface;
        }

        return findFallbackInterface();
    }

    public void timer()
    {
         timer=new Timer();
         task = new TimerTask() {
            @Override
            public void run() {
                stopTrafficFlow();
                System.out.println("timer working");
                timer.cancel();
            }
        };
        timer.schedule(task, 15000,15000);

    }

    public void stopTrafficFlow() {
        if (mWorker != null) {
            mWorker.cancel(true);
        }
    }
    private String removeTimeStamp(String flowDump){
        int i = 0;
        int comma = 0;
        while ( (i <= flowDump.length()) && (comma < 6) ){
            if (flowDump.charAt(i) == ',' ){
                comma++;
            }
            i++;
        }
        return flowDump.replace(flowDump.substring(i,i+23),"");
    }
    private void insertFlow(BasicFlow flow) {
        String flowDump = flow.dumpFlowBasedFeaturesEx();

        if (currentSaveDir == null || currentSaveFileName == null) {
            String path = FlowMgr.getInstance().getAutoSaveFile();
            File saveFile = new File(path);
            File saveParent = saveFile.getParentFile();
            if (saveParent != null && !saveParent.exists()) {
                saveParent.mkdirs();
            }
            currentSaveDir = (saveParent == null) ? FlowMgr.getInstance().getSavePath() : saveParent.getPath();
            currentSaveFileName = saveFile.getName();
        }

        csvWriterThread.execute(new InsertCsvRow(FlowFeature.getHeader(), flowDump, currentSaveDir, currentSaveFileName));

        try {
            // Sending flow to Python model is best-effort and should not block CSV persistence.
            count = count + 1;
            System.out.println(count);
            if (dout != null) {
                dout.writeUTF(removeTimeStamp(flowDump)+"bpoint");
                dout.flush();
            }
        } catch (Exception ex) {
            logger.warn("Unable to send flow to model socket: {}", ex.getMessage());
        }
    }
    
}