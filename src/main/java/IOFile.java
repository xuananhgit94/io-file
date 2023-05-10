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

import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        String action = "";
        try {
            fileItems = uploader.parseRequest(request);
            for (FileItem item : fileItems) {
                if (item.isFormField() && item.getFieldName().equals("action")) {
                    action = item.getString();
                    break;
                }
            }
            if (action.equals("bankList")) {
                mergeBankList(response, fileItems);
            } else {
                mergeBankBranchList(response, fileItems);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mergeBankBranchList(HttpServletResponse response, List<FileItem> fileItems) throws IOException {
        InputStream fileExcel = null;
        InputStream fileJson = null;
        for (var fileItem : fileItems) {
            if (Objects.isNull(fileItem.getName())) {
                continue;
            }
            if (fileItem.getName().endsWith(".json")) {
                fileJson = fileItem.getInputStream();
            } else {
                fileExcel = fileItem.getInputStream();
            }
        }
        var dataExcels = readFileExcelBranch(fileExcel);
        var dataJsons = readFileJson(fileJson);
        Map<String, List<Map<String, Object>>> map = dataExcels.stream().collect(Collectors.groupingBy(x -> String.valueOf(x.get("BANK_CODE"))));
        map.forEach((k, v) -> {
            map.put(k, mergeDataBranch(dataExcels ,dataJsons, v));
        });
        Map<String, List<Map<String, Object>>> mapResult = getResult(dataJsons, map);
        System.out.println("data json = " + dataJsons.size());
        System.out.println("count bank update = " + mapResult.size());
        Map<String, List<Map<String, Object>>> data = dataExcels.stream().collect(Collectors.groupingBy(x -> String.valueOf(x.get("BANK_CODE"))));
        data.forEach((k, v) -> System.out.println(v));
        File file = prepareJsonBankBranchFile(mapResult);
        downloadBranch(file, response);
    }

    private Map<String, List<Map<String, Object>>> getResult(List<Map<String, Object>> dataJsons, Map<String, List<Map<String, Object>>> map) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        dataJsons.forEach(x -> {
            String bankCode = String.valueOf(x.get("BANK_CODE"));
            AtomicBoolean isBank = new AtomicBoolean(true);
            map.forEach((k, v) -> {
                if (k.equals(bankCode)) {
                    isBank.set(false);
                    result.put(bankCode, v);
                }
            });
            if (isBank.get()) {
                System.out.println(x);
            }
        });
        return result;
    }

    private File prepareJsonBankBranchFile(Map<String, List<Map<String, Object>>> map) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.convertValue(map, JsonNode.class);
        File jsonFile = new File("bank_branches.json");
        objectMapper.writeValue(jsonFile, jsonNode);
        return jsonFile;
    }

    private List<Map<String, Object>> mergeDataBranch(List<Map<String, Object>> dataExcelFile, List<Map<String, Object>> dataJsons, List<Map<String, Object>> dataExcels) {
        var result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> dataJson : dataJsons) {
            Map<String, Object> dataMerge = new HashMap<>();
            int bankCodeJson = Integer.parseInt(dataJson.get("BANK_CODE").toString());
            for (Map<String, Object> dataExcel : dataExcels) {
                int bankCodeExcel = Integer.parseInt(dataExcel.get("BANK_CODE").toString());
                if (bankCodeJson == bankCodeExcel) {
                    dataMerge.put("BANK_ORDER", dataJson.get("BANK_ORDER"));
                    dataMerge.put("BANK_KEY", dataJson.get("BANK_KEY"));
                    dataMerge.put("BRANCH_CODE", dataExcel.get("BRANCH_CODE"));
                    dataMerge.put("BRANCH_NAME", dataExcel.get("CITAD_NAME"));
                    dataMerge.put("BEN_ID", dataJson.get("BEN_ID"));
                    dataMerge.put("BANK_NAME", dataJson.get("BANK_NAME"));
                    result.add(dataMerge);
                    dataExcelFile.remove(dataExcel);
                }
            }
        }
        return result;
    }

    private void mergeBankList(HttpServletResponse response, List<FileItem> fileItems) throws IOException {
        InputStream fileExcel = null;
        InputStream fileJson = null;
        for (var fileItem : fileItems) {
            if (Objects.isNull(fileItem.getName())) {
                continue;
            }
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
    private void downloadBranch(File file, HttpServletResponse response) throws IOException {
        FileInputStream inputStream = new FileInputStream(file);
        ServletOutputStream outputStream = response.getOutputStream();

        response.setContentType("application/json");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        inputStream.close();
        outputStream.flush();
        outputStream.close();
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
        return mergedList;
    }

    private List<Map<String, Object>> readFileJson(InputStream fileJson) throws IOException {
        var jsonList = new ArrayList<Map<String, Object>>();
        var mapper = new ObjectMapper();
        var rootNode = mapper.readTree(fileJson);
        if (rootNode.isArray()) {
            for (var node : rootNode) {
                Map<String, Object> map = mapper.convertValue(node, new TypeReference<>() {});
                jsonList.add(map);
            }
        } else  {
            Map<String, Object> map = mapper.convertValue(rootNode, new TypeReference<>() {});
            jsonList.add(map);
        }
        return jsonList;
    }

    private List<Map<String, Object>> readFileExcelBranch(InputStream fileExcel) throws IOException {
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
                } else if (headerNames.get(j).equals("CITAD_CD")) {
                    data.put("BRANCH_CODE", cell.getStringCellValue());
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
            IntStream.range(0, headerNames.size()).forEach(j -> {
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
            });
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
