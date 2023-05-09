<%--
  Created by IntelliJ IDEA.
  User: xuananh
  Date: 09/05/2023
  Time: 08:03
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
  <head>
    <title>$Title$</title>
  </head>
  <body>
    <form action="MergeFileServlet" method="post" enctype="multipart/form-data">
      <label for="excelFile">Select an Excel file and Json file to upload:</label>
      <input type="file" name="excelFile" id="excelFile" multiple>
      <br>
      <input type="submit" value="Download">
    </form>
  </body>
</html>
