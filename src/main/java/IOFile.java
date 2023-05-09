import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@WebServlet("/MergeFileServlet")
public class IOFile extends HttpServlet {
    private ServletFileUpload uploader = null;

    @Override
    public void init() {
        var fileFactory = new DiskFileItemFactory();
        var filesDir = (File) getServletContext().getAttribute("FILES_DIR_FILE");
        fileFactory.setRepository(filesDir);
        this.uploader = new ServletFileUpload(fileFactory);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        List<FileItem> fileItems;
        try {
            fileItems = uploader.parseRequest(request);
            InputStream fileExcel = null;
            InputStream fileJson = null;
            for (var fileItem : fileItems) {
                if (fileItem.getName().endsWith(".json")) {
                    fileJson = fileItem.getInputStream();
                } else {
                    fileExcel = fileItem.getInputStream();
                }
            }
            var dataExcels = readFileExcel(fileExcel);
            var dataJsons = readFileJson(fileJson);
            var mergeData = mergeData(dataJsons, dataExcels);
            var fileDownload = prepareJsonFile(mergeData);
            System.out.println("data excel = " + dataExcels.size());
            System.out.println("data json = " + dataJsons.size());
            System.out.println("data merge = " + mergeData.size());
            download(fileDownload, response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File prepareJsonFile(List<Map<String, Object>> dataMerge) throws IOException {
        var objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        var jsonString = objectMapper.writeValueAsString(dataMerge);

        var outputFile = new File("jsonfile" + ".json");
        try (var outputStream = new FileOutputStream(outputFile)) {
            outputStream.write(jsonString.getBytes(StandardCharsets.UTF_8));
        }
        return outputFile;
    }

    private void download(File file, HttpServletResponse response) throws IOException {
        try (var inputStream = new FileInputStream(file)) {
            response.setContentType("application/json");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            IOUtils.copy(inputStream, response.getOutputStream());
            response.flushBuffer();
        }
    }

    private List<Map<String, Object>> mergeData(List<Map<String, Object>> dataJson, List<Map<String, Object>> dataExel) {
        var mergedList = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> sporadicExelData = new ArrayList<>(dataExel);
        Map<String, Object> dataTemplate = dataExel.get(0);
        for (Map<String, Object> jsonData : dataJson) {
            int jsonBENID = Integer.parseInt(jsonData.get("BEN_ID").toString());
            boolean isProcess = true;
            if (jsonBENID != 0) {
                for (Map<String, Object> data : dataExel) {
                    int excelBenId = Integer.parseInt(data.get("BEN_ID").toString());
                    if (jsonBENID == excelBenId) {
                        Map<String, Object> mergedData = new LinkedHashMap<>(jsonData);
                        data.forEach(mergedData::put);
                        mergedList.add(mergedData);
                        sporadicExelData.remove(data);
                        isProcess = false;
                        break;
                    }
                }
            }
            if (isProcess) {
                Map<String, Object> mergedData = new LinkedHashMap<>(jsonData);
                for (String key : dataTemplate.keySet()) {
                    if (!key.equals("BEN_ID")) {
                        if (dataTemplate.get(key) instanceof Number) {
                            mergedData.put(key, 0);
                        } else {
                            mergedData.put(key, "");
                        }
                    }
                }
                mergedList.add(mergedData);
            }
        }
        for (var excelData : sporadicExelData) {
            System.out.println(excelData);
        }
        return mergedList;
    }

    private List<Map<String, Object>> readFileJson(InputStream fileJson) throws IOException {
        var jsonList = new ArrayList<Map<String, Object>>();
        var mapper = new ObjectMapper();
        var rootNode = mapper.readTree(fileJson);
        if (rootNode.isArray()) {
            for (JsonNode node : rootNode) {
                Map<String, Object> map = mapper.convertValue(node, new TypeReference<>() {});
                jsonList.add(map);
            }
        } else  {
            Map<String, Object> map = mapper.convertValue(rootNode, new TypeReference<>() {});
            jsonList.add(map);
        }
        return jsonList;
    }

    private List<Map<String, Object>> readFileExcel(InputStream fileExcel) throws IOException {
        var dataList = new ArrayList<Map<String, Object>>();
        var workbook = new XSSFWorkbook(fileExcel);
        var sheet = workbook.getSheetAt(0);
        var headerRow = sheet.getRow(0);
        var headerNames = new ArrayList<String>();
        for (var cell : headerRow) {
            headerNames.add(cell.getStringCellValue());
        }
        var rowCount = sheet.getLastRowNum();
        for (var i = 1; i <= rowCount; i++) {
            var row = sheet.getRow(i);
            Map<String, Object> data = new HashMap<>();
            for (var j = 0; j < headerNames.size(); j++) {
                var cell = row.getCell(j);
                if (cell == null) {
                    data.put(headerNames.get(j), null);
                } else if (cell.getCellType() == CellType.NUMERIC) {
                    data.put(headerNames.get(j).strip().toUpperCase(), (int) cell.getNumericCellValue());
                } else {
                    if (isInteger(cell.getStringCellValue())) {
                        data.put(headerNames.get(j).strip().toUpperCase(), Integer.parseInt(cell.getStringCellValue()));
                    } else {
                        data.put(headerNames.get(j).strip().toUpperCase(), cell.getStringCellValue().strip());
                    }
                }
            }
            dataList.add(data);
        }
        workbook.close();
        return dataList;
    }

    private boolean isInteger(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
