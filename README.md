# backend-fede — Padrón FEDERA

API REST del padrón de productores de la federación. El cliente Flutter vive en
el repositorio `frontend-fede`.

Jerarquía del dominio: **Federación › Central › Sindicato › Productor**, y cada
productor tiene lotes, observaciones y fotografías.

## Requisitos

- **Java 17** (probado con 17.0.12). No sirve una versión mayor sin más: OpenPDF
  quedó fijo en la 2.0.5 justamente porque de la 2.1.0 en adelante compila a
  Java 21.
- **MariaDB** escuchando en `localhost:3307`. La base `federa` se crea sola
  gracias a `createDatabaseIfNotExist=true`.

## Cómo correrlo

```bash
./mvnw spring-boot:run
```

Queda en `http://localhost:8080`. La documentación de la API, con todos los
endpoints y sus reglas, en `http://localhost:8080/swagger-ui.html`.

## Configuración

Las credenciales van como `${VARIABLE:valor-por-defecto}`. En una máquina de
desarrollo no hay que configurar nada. Para cualquier otra:

| Variable | Para qué | Por defecto |
|---|---|---|
| `DB_URL` | Conexión a MariaDB | `localhost:3307/federa` |
| `DB_USUARIO` / `DB_CONTRASENA` | Credenciales de la base | `root` / `root` |
| `JWT_CLAVE` | Firma de los tokens, base64, mínimo 32 bytes | vacía |
| `ADMIN_USUARIO` / `ADMIN_CONTRASENA` | Usuario que se crea la primera vez | `admin` / `admin` |

Con `JWT_CLAVE` vacía se genera una al arrancar: sirve para probar, pero las
sesiones se caen en cada reinicio.

**Ninguna contraseña de verdad debe entrar al repositorio.** Una vez que queda
en el historial de git, sacarla obliga a reescribir el historial entero.

El inicio de sesión ya funciona y emite tokens válidos, pero
`federa.seguridad.exigir-autenticacion` está en `false`: la API responde sin
token para no romper al cliente mientras no tenga pantalla de login.

## Migraciones

`ddl-auto=update` crea columnas pero no las rellena, no relaja un `NOT NULL` ni
renombra nada. Lo que no puede hacer solo está en `src/main/resources/db`, en
orden:

1. `auditoria-y-estado.sql` — `created_at`, `updated_at` y `estado` en todas las
   tablas. Renombra `lotes.estado` a `estado_lote`, porque ese nombre lo pasó a
   ocupar el booleano de habilitado.
2. `directorio-por-nivel.sql` — el directorio deja de ser solo de los sindicatos.
3. `codigo-y-reuniones.sql` — el código de credencial de cada productor y las
   tablas de reuniones.

Los tres son **repetibles**: comprueban contra `information_schema` antes de
tocar nada, así que correrlos dos veces no hace daño.

```bash
mysql -u root -p -P 3307 -h 127.0.0.1 federa < src/main/resources/db/auditoria-y-estado.sql
```

En una base vacía no hacen falta: Hibernate crea el esquema completo solo.

## Las fotos

No van a la base ni al repositorio: se guardan en disco bajo
`federa.almacenamiento.raiz` y la base solo conserva la clave. La carpeta
`almacenamiento/` está en `.gitignore` porque son datos, no código.

En producción conviene una ruta **absoluta fuera del directorio de la
aplicación**: si queda adentro, un redespliegue que limpie la carpeta se lleva
las fotos por delante.

## Pruebas

```bash
./mvnw test
```

Son pruebas de unidad: no necesitan base ni servidor. Las que verifican los PDF
los generan con datos inventados y leen de vuelta el resultado, y dejan una
muestra en `target/` para revisarla a ojo.

Las pruebas de integración de la API viven en el repositorio del cliente, que es
donde está el código que la consume.

## Estructura

```
src/main/java/com/federa/backend/
├── almacen/      almacén de objetos en disco, con borrado transaccional
├── config/       rutas, CORS, OpenAPI, auditoría
├── controller/   los endpoints
├── dto/          lo que entra y lo que sale
├── exception/    errores de negocio y su traducción a HTTP
├── model/        las entidades y sus reglas
├── repository/   consultas
├── seguridad/    JWT, filtro y configuración de Spring Security
└── service/      la lógica: importación, imágenes, PDF, directorio, reuniones
```
