### Building the request

A request is composed by several components:
* "query": A SQL query, potentially parameterized with HTTP request parameters or "mother" query results (for sub-queries):
    * A parameter is declared as follows: ${myParameter} or $[myParameter] to escape quotes in SQL strings.
    * Main query: It will be replaced with the value of the value of the custom parameter with the same name, or if not found with the value of the HTTP parameter with the same name.
    * Sub-query: It will be replaced with the value of the column with the same name, or if not found with the value of the value of the custom parameter with the same name, or if not found with the value of the HTTP parameter with the same name.
    * A parameter with no replacement values will be replaced by an empty String.
* "conditionQuery": A SQL query meaning to return a value (1 line and column). potentially parameterized with HTTP request parameters or "mother" query results (for sub-queries).
* "conditions": Map of possible result values for "conditionQuery", for which a specific request can be performed. This allows personnalizing the result and its structure, depending on data. Following predefined values are handled:
    * "undefined" if no result (no line).
    * "null" if value is null (a line with null field).
    * "default" for non null values but not specified in conditions.
* "mask": A list of column names of the query results which will not appear in the generated JSON. This allows retrieving values to use as parameters for sub-requests. Other trick, use "select*" and mask one column instead of listing all needed columns, the request will be sorter.
* "aliases": Alternative to parameter sub-objects and fields names, instead of using columns names.
* "subqueries": It's simply a Map of [String ; Request]. The key is the JSON field name of the future sub-list or sub-object to retrieve and the request is again all of the components "query", "subqueries", etc ... (except "noLines"). It's hence possible to interlock an infinite number of sub-requests.
    * The JSON field name can be formated with "__" separators to introduced several levels of Objects.
    * The Request can also be an array of Requests, in which case the JSON field will contain an array of all the results.
* "resultType": Tells if the expected result is a value ("value"), a unique object ("object"), an array ("array") or a list ("list"). List by default. If "object" and several objects returned by the query, then only the first one is taken into account, the others are ignored. If "value" and several columns, then only the first one is taken into account, the others are ignored. If "array" and several columns, then only the first one is taken into account, the others are ignored.
* "noLines": If no lines are returned by the SQL query the HTTP status, status message and response can be overridden with this parameter.
* "customParameters": Allows creating new parameters, eventually derived from existing ones. See dedicated chapter.

"query" and "conditionQuery" can not be used together. If "conditionQuery" is used, presence of "conditions" is mandatory.