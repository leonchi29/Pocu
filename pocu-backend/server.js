const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
require('dotenv').config();
const sql = require('mssql');
const jwt = require('jsonwebtoken');

const app = express();

// Middleware
app.use(cors());
app.use(bodyParser.json());

// Logs de depuración
console.log('🔍 Variables de entorno cargadas:');
console.log('DB_HOST:', process.env.DB_HOST);
console.log('DB_USER:', process.env.DB_USER);
console.log('DB_NAME:', process.env.DB_NAME);
console.log('DB_PASSWORD:', process.env.DB_PASSWORD ? '✅ Cargada' : '❌ No cargada');
console.log('JWT_SECRET:', process.env.JWT_SECRET ? '✅ Cargada' : '❌ No cargada');

// Configuración de SQL Server (Azure SQL Database)
const config = {
    server: process.env.DB_HOST,
    port: parseInt(process.env.DB_PORT || '1433'),
    authentication: {
        type: 'default',
        options: {
            userName: process.env.DB_USER,
            password: process.env.DB_PASSWORD
        }
    },
    options: {
        database: process.env.DB_NAME,
        trustServerCertificate: false,
        encrypt: true,
        connectionTimeout: 30000,
        requestTimeout: 30000,
        enableArithAbort: true,
        cryptoCredentialsDetails: {
            minVersion: 'TLSv1_2'
        }
    }
};


let pool;

// Inicializar conexión a SQL Server
async function initializeDatabase() {
    try {
        pool = new sql.ConnectionPool(config);
        await pool.connect();
        console.log('✅ Conectado a SQL Server');
    } catch (err) {
        console.error('❌ Error conectando a SQL Server:', err);
        setTimeout(initializeDatabase, 5000);
    }
}

// Health Check
app.get('/api/health', async (req, res) => {
    try {
        if (!pool) {
            return res.status(500).json({ success: false, message: 'Database not connected' });
        }
        res.json({ success: true, message: 'Servidor activo' });
    } catch (err) {
        res.status(500).json({ success: false, message: err.message });
    }
});

// ============ ENDPOINTS DE ESTUDIANTES ============

// Buscar estudiante por RUT
app.post('/api/students/search', async (req, res) => {
    try {
        const { rut } = req.body;

        if (!rut) {
            return res.status(400).json({
                found: false,
                message: 'RUT es requerido'
            });
        }

        const request = pool.request();
        request.input('rut', sql.NVarChar, rut);

        const result = await request.query(`
            SELECT
                a.IDAlumno as student_id,
                a.Rut as rut,
                a.Nombre as nombre,
                a.Apellido as apellido,
                c.IDCurso as course_id,
                c.Curso as course_name,
                col.Nombre as school_name,
                col.IDColegio as school_id
            FROM Alumnos a
            INNER JOIN Cursos c ON a.IDCurso = c.IDCurso
            INNER JOIN Colegio col ON c.IDColegio = col.IDColegio
            WHERE a.Rut = @rut
        `);

        if (result.recordset.length > 0) {
            const student = result.recordset[0];
            return res.json({
                found: true,
                student_id: student.student_id,
                name: student.nombre + ' ' + student.apellido,
                school_name: student.school_name,
                course_name: student.course_name
            });
        } else {
            return res.json({
                found: false,
                message: 'Estudiante no registrado en ningún colegio'
            });
        }
    } catch (err) {
        console.error('Error en search:', err);
        res.status(500).json({
            found: false,
            message: err.message
        });
    }
});

// Registrar dispositivo de estudiante
app.post('/api/students/register-device', async (req, res) => {
    try {
        const {
            student_id,
            student_rut,
            device_id,
            device_serial,
            device_model,
            device_name,
            android_version,
            email,
            app_version
        } = req.body;

        // Generar token JWT para el dispositivo
        const deviceToken = jwt.sign(
            { device_id, student_id },
            process.env.JWT_SECRET || 'super_secret_key',
            { expiresIn: '365d' }
        );

        const request = pool.request();

        // Insertar dispositivo en tabla Dispositivos
        request.input('student_id', sql.Int, student_id);
        request.input('device_serial', sql.NVarChar, device_serial);
        request.input('device_model', sql.NVarChar, device_model);
        request.input('device_name', sql.NVarChar, device_name);

        await request.query(`
            INSERT INTO Dispositivos (IDAlumno, Serial, Modelo, Marca, NumTel)
            VALUES (@student_id, @device_serial, @device_model, @device_model, @device_name)
        `);

        // Obtener horarios del curso del alumno
        const scheduleRequest = pool.request();
        scheduleRequest.input('student_id', sql.Int, student_id);

        const schedules = await scheduleRequest.query(`
            SELECT
                h.IDHorarios as id,
                h.Dia as day_of_week,
                h.Inicio as start_time,
                h.Fin as end_time
            FROM Horarios h
            INNER JOIN Cursos c ON h.IDCursos = c.IDCurso
            INNER JOIN Alumnos a ON a.IDCurso = c.IDCurso
            WHERE a.IDAlumno = @student_id
            ORDER BY h.Inicio
        `);

        res.json({
            success: true,
            message: 'Dispositivo registrado correctamente',
            device_token: deviceToken,
            schedules: schedules.recordset,
            allowed_apps: ['com.android.vending', 'com.android.settings']
        });

    } catch (err) {
        console.error('Error en register-device:', err);
        res.status(500).json({
            success: false,
            message: err.message
        });
    }
});

// ============ ENDPOINTS DE DISPOSITIVOS ============

// Heartbeat - el dispositivo se reporta
app.post('/api/devices/heartbeat', async (req, res) => {
    try {
        const { device_id, timestamp, is_service_enabled, is_lockdown_mode, permissions, battery_level } = req.body;
        const token = req.headers.authorization?.replace('Bearer ', '');

        // Verificar token
        if (!token) {
            return res.status(401).json({ success: false, message: 'Token requerido' });
        }

        try {
            jwt.verify(token, process.env.JWT_SECRET || 'super_secret_key');
        } catch (err) {
            return res.status(401).json({ success: false, message: 'Token inválido' });
        }

        res.json({
            success: true,
            commands: []
        });

    } catch (err) {
        console.error('Error en heartbeat:', err);
        res.status(500).json({ success: false, message: err.message });
    }
});

// Registrar alerta
app.post('/api/alerts', async (req, res) => {
    try {
        const { device_id, event_type, details } = req.body;
        const token = req.headers.authorization?.replace('Bearer ', '');

        if (!token) {
            return res.status(401).json({ success: false, message: 'Token requerido' });
        }

        console.log('📢 Alerta recibida:', { device_id, event_type, details });

        res.json({ success: true, message: 'Alerta registrada' });

    } catch (err) {
        console.error('Error en alerts:', err);
        res.status(500).json({ success: false, message: err.message });
    }
});

// ============ ENDPOINTS DE CONFIGURACIÓN ============

// Obtener configuración remota
app.get('/api/devices/:deviceId/config', async (req, res) => {
    try {
        const { deviceId } = req.params;
        const token = req.headers.authorization?.replace('Bearer ', '');

        if (!token) {
            return res.status(401).json({ success: false, message: 'Token requerido' });
        }

        // Obtener horarios
        const scheduleRequest = pool.request();
        const schedules = await scheduleRequest.query(`
            SELECT
                h.IDHorarios as id,
                h.Dia as day_of_week,
                h.Inicio as start_time,
                h.Fin as end_time
            FROM Horarios
            ORDER BY h.Inicio
        `);

        res.json({
            success: true,
            schedules: schedules.recordset,
            allowed_apps: ['com.android.vending', 'com.android.settings'],
            lockdown_enabled: true,
            service_enabled: true
        });

    } catch (err) {
        console.error('Error en config:', err);
        res.status(500).json({ success: false, message: err.message });
    }
});

// Puerto
const PORT = process.env.PORT || 3000;

// Inicializar y arrancar servidor
initializeDatabase().then(() => {
    app.listen(PORT, () => {
        console.log(`🚀 Servidor corriendo en http://localhost:${PORT}`);
        console.log(`📊 Base de datos: ${process.env.DB_NAME}`);
        console.log(`👤 Usuario: ${process.env.DB_USER}`);
    });
});

module.exports = app;
