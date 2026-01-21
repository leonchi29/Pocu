-- Ejecuta esto en SQL Server Management Studio para ver tu estructura:

-- Ver todas las tablas
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE';

-- Ver columnas de tabla Horarios
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Horarios';

-- Ver columnas de tabla Dispositivos (o Devices)
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Dispositivos';

-- Ver columnas de tabla Estudiantes (o Students)
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Estudiantes';

-- Ver algunos registros de ejemplo:
SELECT TOP 5 * FROM Horarios;
SELECT TOP 5 * FROM Dispositivos;
SELECT TOP 5 * FROM Estudiantes;
