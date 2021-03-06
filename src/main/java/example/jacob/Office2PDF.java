package example.jacob;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.LibraryLoader;
import com.jacob.com.Variant;

public class Office2PDF implements Office2PDFInterface {

    private static Office2PDF instance;

    public static Office2PDF getInstance() throws IOException {
        if (instance == null) {
            instance = new Office2PDF();
        }
        return instance;
    }

    private Office2PDF() throws IOException {
        loadLibrary();
    }

    private static void loadLibrary() throws IOException {
        String libFile = "amd64".equals(System.getProperty("os.arch")) ? "jacob-1.18-x64.dll" : "jacob-1.18-x86.dll";
        String libPath = Office2PDF.class.getResource("/").getPath();
        System.setProperty(LibraryLoader.JACOB_DLL_PATH, libPath + libFile);
        LibraryLoader.loadJacobLibrary();
    }

    private enum OfficeType {

        word("Word.Application", 17), excel("Excel.Application", 0), ppt("PowerPoint.Application", 32);

        private final String oleName;
        private final int pdfMagicNumber;

        OfficeType(String oleName, int pdfMagicNumber) {
            this.oleName = oleName;
            this.pdfMagicNumber = pdfMagicNumber;
        }
    }

    private OfficeType type;
    private ActiveXComponent oleComponent = null;
    private Dispatch activeDoc = null;

    // Constants that map onto Word's WdSaveOptions enumeration and that
    // may be passed to the close(int) method
    public static final int DO_NOT_SAVE_CHANGES = 0;
    public static final int PROMPT_TO_SAVE_CHANGES = -2;
    public static final int SAVE_CHANGES = -1;

    @Override
    public File convert2PDF(File file, String fileType, String outputPath) throws Exception {

        switch (fileType) {
            case "doc" :
            case "docx" :
            case "word" :
                this.type = OfficeType.word;
                this.oleComponent = new ActiveXComponent(type.oleName);
                this.oleComponent.setProperty("Visible", new Variant(false));
                break;
            case "xls" :
            case "xlsx" :
            case "excel" :
                this.type = OfficeType.excel;
                this.oleComponent = new ActiveXComponent(type.oleName);
                this.oleComponent.setProperty("Visible", new Variant(false));
                break;
            case "ppt" :
            case "pptx" :
            case "powerpoint" :
                this.type = OfficeType.ppt;
                this.oleComponent = new ActiveXComponent(type.oleName);
                break;
            default :
                throw new Exception("Unsuported content type " + fileType);

        }
        String inputPath = file.getAbsolutePath();
        String defaultOutputPath = file.getAbsolutePath() + "_.pdf";
        if (StringUtils.isEmpty(outputPath)) {
            outputPath = defaultOutputPath;
        }

        try {
            openDoc(inputPath);
            publishAsPDF(outputPath);
            closeDoc();
        } finally {
            quit();
        }
        return new File(outputPath);
    }

    private void openDoc(String docName) {
        Variant var;
        switch (type) {
            case word :
                var = Dispatch.get(this.oleComponent, "Documents");
                this.activeDoc = Dispatch.call(var.getDispatch(), "Open", docName).getDispatch();
                break;
            case excel :
                var = Dispatch.get(this.oleComponent, "Workbooks");
                this.activeDoc = Dispatch.call(var.getDispatch(), "Open", docName).getDispatch();
                break;
            case ppt :
                var = Dispatch.get(this.oleComponent, "Presentations");
                this.activeDoc = Dispatch.call(var.toDispatch(), "Open", docName, true, //ReadOnly
                        true, //Untitled
                        false//WithWindow
                ).toDispatch();
                break;
        }
    }

    /**
     * There is more than one way to convert the document into PDF format, you can either explicitly use a FileConvertor
     * object or call the ExportAsFixedFormat method on the active document. This method opts for the latter and calls the
     * ExportAsFixedFormat method passing the name of the file along with the integer value of 17. This value maps onto one
     * of Word's constants called wdExportFormatPDF and causes the application to convert the file into PDF format. If you
     * wanted to do so, for testing purposes, you could add another value to the args array, a Boolean value of true. This
     * would open the newly converted document automatically.
     * 
     * @param filename
     */
    private void publishAsPDF(String filename) {
        // The code to expoort as a PDF is 17
        switch (type) {
            case word :
                Dispatch.call(this.activeDoc, "ExportAsFixedFormat", filename, type.pdfMagicNumber);
                break;
            case excel :
                Dispatch.call(this.activeDoc, "ExportAsFixedFormat", type.pdfMagicNumber, filename);
                break;
            case ppt :
                Dispatch.call(this.activeDoc, "SaveAs", filename, type.pdfMagicNumber);
                break;
        }
    }

    /**
     * Called to close the active document. Note that this method simply calls the overloaded closeDoc(int) method passing
     * the value 0 which instructs Word to close the document and discard any changes that may have been made since the
     * document was opened or edited.
     */
    private void closeDoc() {
        closeDoc(DO_NOT_SAVE_CHANGES);
    }

    /**
     * Called to close the active document. It is possible with this overloaded version of the close() method to specify
     * what should happen if the user has made changes to the document that have not been saved. There are three possible
     * value defined by the following manifest constants; DO_NOT_SAVE_CHANGES - Close the document and discard any changes
     * the user may have made. PROMPT_TO_SAVE_CHANGES - Display a prompt to the user asking them how to proceed.
     * SAVE_CHANGES - Save the changes the user has made to the document.
     *
     * @param saveOption A primitive integer whose value indicates how the close operation should proceed if the user has
     *            made changes to the active document. Note that no checks are made on the value passed to this argument.
     */
    private void closeDoc(int saveOption) {
        //Object args = {new Integer(saveOption)};
        switch (type) {
            case word :
            case excel :
                Dispatch.call(this.activeDoc, "Close", saveOption);
                break;
            case ppt :
                Dispatch.call(this.activeDoc, "Close");
                break;
        }

    }

    /**
     * Called once processing has completed in order to close down the instance of Word.
     */
    private void quit() {
        Dispatch.call(this.oleComponent, "Quit");
    }

    /**
     * 
     */
    @Override
    public File convert2PDF(File file) throws Exception {

        String contentType = new Tika().detect(file);

        switch (contentType) {
            case "doc" :
            case "docx" :
                this.type = OfficeType.word;
                this.oleComponent = new ActiveXComponent(type.oleName);
                //setting new Variant(false) cause the app not to be visible
                this.oleComponent.setProperty("Visible", new Variant(false));
                break;
            case "xls" :
            case "xlsx" :
                this.type = OfficeType.excel;
                this.oleComponent = new ActiveXComponent(type.oleName);
                this.oleComponent.setProperty("Visible", new Variant(false));
                break;
            case "ppt" :
            case "pptx" :
                this.type = OfficeType.ppt;
                this.oleComponent = new ActiveXComponent(type.oleName);
                break;
            default :
                throw new Exception("Unsuported content type " + contentType);

        }
        String inputPath = file.getAbsolutePath();
        String outputPath = file.getAbsolutePath() + "_.pdf";
        try {
            openDoc(inputPath);
            publishAsPDF(outputPath);
            closeDoc();
        } finally {
            quit();
        }
        return new File(outputPath);
    }

//    @Override
//    public void convert2PDF(File msoFile, String fileType, String outputPath) throws Exception {
//        Optional<String> value = Optional.ofNullable(outputPath);
//        value.ifPresent(o -> setOutputPath(outputPath));
//        this.convert2PDF(msoFile, fileType);
//    }
}
