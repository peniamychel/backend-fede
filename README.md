# backend-fede — Padrón FEDERA

API REST del padrón de productores de la federación. El cliente Flutter vive en
el repositorio `frontend-fede`.

Jerarquía del dominio: **Federación › Central › Sindicato › Productor**, y cada
productor tiene lotes y fotografías.

## Requisitos

- **Java 17** (probado con 17.0.12). No sirve una versión mayor sin más: OpenPDF
  quedó fijo en la 2.0.5 justamente porque de la 2.1.0 en adelante compila a
  Java 21.
- **MariaDB** escuchando en `localhost:3307`. La base `federa` se crea sola
  gracias a `createDatabaseIfNotExist=true`.

## Cómo correrlo

La primera vez, crear la configuración local a partir de la plantilla:

```powershell
Copy-Item .env.example .env
```

Editar `.env` con las credenciales de MariaDB y un token SIE vigente. El
archivo se carga automáticamente al iniciar desde la raíz del backend y está
excluido de Git.

```bash
./mvnw spring-boot:run
```

Queda en `http://localhost:8080`. La documentación de la API, con todos los
endpoints y sus reglas, en `http://localhost:8080/swagger-ui.html`.

## Desarrollo local y entorno Docker

El proyecto mantiene dos ambientes independientes:

- **Desarrollo**: este backend desde IntelliJ o `./mvnw spring-boot:run`, la
  MariaDB local del puerto 3307 y Flutter ejecutado desde su repositorio.
- **Docker**: MariaDB, backend y Flutter Web aislados en contenedores, con su
  propia base y sus propios archivos. Sirve como entorno de integración antes
  de considerar un cambio listo para producción.

La primera vez, preparar las variables del entorno Docker:

```powershell
Copy-Item .env.docker.example .env.docker
```

Editar las contraseñas y, cuando corresponda, el token SIE. Luego:

```powershell
docker compose --env-file .env.docker up -d --build
docker compose --env-file .env.docker ps
```

La aplicación queda en `http://localhost/` y Swagger en
`http://localhost/swagger-ui.html`. Desde otro equipo de la intranet se usa la
IP de esta computadora, sin agregar el puerto 8080.

Para reconstruir después de cambiar código:

```powershell
# Todo el entorno
docker compose --env-file .env.docker up -d --build

# Solo el backend
docker compose --env-file .env.docker up -d --build federa-backend

# Solo Flutter Web
docker compose --env-file .env.docker up -d --build federa-web
```

Detener los contenedores conserva la base, imágenes y respaldos:

```powershell
docker compose --env-file .env.docker down
```

No agregar `-v`: `docker compose down -v` elimina deliberadamente los tres
volúmenes del entorno Docker. Desarrollo y Docker pueden estar levantados a la
vez porque Docker solo publica el puerto 80; su backend y su MariaDB no exponen
8080 ni 3306/3307 en Windows.

## Configuración

Las credenciales se leen del `.env` local o de variables del sistema. Las
variables del sistema tienen prioridad, lo que permite usar el mismo artefacto
en producción sin copiar el archivo local.

| Variable | Para qué | Ejemplo de desarrollo |
|---|---|---|
| `DB_URL` | Conexión a MariaDB | `localhost:3307/federa` |
| `DB_USUARIO` / `DB_CONTRASENA` | Credenciales de la base | usuario local de MariaDB |
| `SIE_URL` / `SIE_TOKEN` | Consulta de datos personales por cédula | URL oficial / token vigente |
| `MARIADB_DUMP` | Ejecutable utilizado por los respaldos internos | `C:/Program Files/MariaDB 10.11/bin/mariadb-dump.exe` |
| `JWT_CLAVE` | Firma de los tokens, base64, mínimo 32 bytes | vacía |
| `ADMIN_USUARIO` / `ADMIN_CONTRASENA` | Usuario que se crea la primera vez | `admin` / `admin` |

Con `JWT_CLAVE` vacía se genera una al arrancar: sirve para probar, pero las
sesiones se caen en cada reinicio.

Los respaldos automáticos no muestran una confirmación: se ejecutan diariamente
a las 02:00 de `America/La_Paz` mientras el backend esté encendido. En Docker el
ejecutable se configura como `/usr/bin/mariadb-dump`; en desarrollo, `.env` debe
apuntar a la versión de MariaDB instalada en Windows.

**Ninguna contraseña de verdad debe entrar al repositorio.** Una vez que queda
en el historial de git, sacarla obliga a reescribir el historial entero.

El inicio de sesión ya funciona y emite tokens válidos, pero
`federa.seguridad.exigir-autenticacion` está en `false`: la API responde sin
token para no romper al cliente mientras no tenga pantalla de login.

## Migraciones

`ddl-auto=update` crea columnas pero no las rellena, no relaja un `NOT NULL`, no
renombra y **nunca borra**. Lo que no puede hacer solo está en
`src/main/resources/db`, en orden:

1. `auditoria-y-estado.sql` — `created_at`, `updated_at` y `estado` en todas las
   tablas. Renombra `lotes.estado` a `estado_lote`, porque ese nombre lo pasó a
   ocupar el booleano de habilitado.
2. `directorio-por-nivel.sql` — el directorio deja de ser solo de los sindicatos.
3. `codigo-y-reuniones.sql` — el código de credencial de cada productor y las
   tablas de reuniones.
4. `lotes-tenencia-y-sistemas.sql` — el lote pasa a colgar del sindicato, y la
   tenencia inicial de cada uno se deriva del productor que tenía.
5. `lotes-ubicacion-y-superficie.sql` — dónde está el lote y cuánto mide.
6. `central-abreviatura.sql` — la central cambia su número por una sigla de tres
   letras. El número se elimina sin convertirse en nada.
7. `productor-correlativo.sql` — el número de cada productor dentro de su
   central, que es la última parte de su código (`2-IVI-1`). Numera por central
   en orden de carga, saltea los números que llevan 666 —el 666, el 1666, el
   2666— y no toca a los que ya tienen número. Al final lista los que hubieran
   quedado con un 666 de antes, para decidirlos a mano.
8. `reuniones-llamadas-y-actas.sql` — la asistencia deja de colgar de la reunión
   y pasa a colgar de la vuelta de lista, porque en una asamblea se llama lista
   varias veces; el acta deja de ser un archivo único y pasa a ser hojas
   ordenadas. Lo que había se convierte en la primera llamada y en la hoja 1.
   Este es el único que **no se puede saltear**: hasta correrlo, `reunion_id`
   sigue `NOT NULL` en `asistencias` y pasar lista falla.
9. `acta-codigo.sql` — el número del acta en el libro del sindicato. Las actas
   ya cargadas quedan sin número: no se puede inventar, hay que ir a mirarlo, y
   la pantalla las marca para ponérselo. De acá en más se exige al subir la
   primera hoja.
10. `directorio-firma-y-sello.sql` — cambia el pie de firma de imagen a texto y
    agrega un sello institucional propio a cada sindicato, central y
    federación. Conserva las imágenes antiguas de pie de firma; no borra datos.
11. `directorio-nuevos-cargos.sql` — reemplaza Presidente y Secretario por los
    cargos reales de cada nivel, conserva sus períodos, firmas y pies de firma,
    y amplía el catálogo con Ejecutivo y Secretario Relaciones.

Aparte hay dos **opcionales**, que limpian lo que quedó huérfano al sacar
funcionalidad. Ninguno hace falta —lo que dejan atrás no molesta— y los dos
borran datos, así que conviene mirar antes qué hay:

- `quitar-observaciones.sql` — la tabla de la bandeja de observaciones.
- `quitar-carnet-productor.sql` — la columna del carné de productor.

Todos son **repetibles**: comprueban antes de tocar nada, así que correrlos dos
veces no hace daño.

```bash
mysql -u root -p -P 3307 -h 127.0.0.1 federa < src/main/resources/db/auditoria-y-estado.sql
```

En una base vacía no hacen falta: Hibernate crea el esquema completo solo.

`productor-observacion-manual.sql` agrega el motivo de la observación hecha
desde la ficha. En las instalaciones con `ddl-auto=update` la columna se crea
al reiniciar el backend; el script queda como alternativa para esquemas
administrados manualmente.

`productor-estado-revision-sie.sql` conserva el resultado de SIE y la
sugerencia pendiente. Los estados de advertencia bloquean la impresión hasta
aceptar la sugerencia o corregir los datos de identidad manualmente.

## Importación de padrón: cédulas repetidas

La importación rechaza una cédula si ya figura en cualquier productor del
padrón, incluso deshabilitado o perteneciente a otra central. Si una cédula
aparece varias veces dentro del Excel, se rechazan todas esas filas y el informe
indica sus números para corregirlas. La comparación ignora espacios y
mayúsculas, pero conserva ceros iniciales y complementos de la cédula.

La simulación y la importación definitiva realizan la misma comprobación.
Con «ignorar filas con error» solo se guardan las filas válidas; sin esa opción,
cualquier rechazo aborta la carga completa. Las cédulas vacías siguen admitidas
como datos pendientes. No se eliminan ni fusionan duplicados históricos.

## Revisión SIE con aprobación

Al abrir un productor importado o verificarlo manualmente, SIE solo propone
correcciones. Si hay diferencias, la respuesta `REQUIERE_CONFIRMACION` incluye
los datos actuales y los propuestos para mostrarlos al usuario. La decisión se
envía a `POST /api/v1/productores/{id}/revision-sie/confirmacion`.

Aceptar valida nuevamente el resultado de SIE antes de guardar; rechazar conserva
los nombres y completa la revisión. Cerrar el diálogo sin elegir no confirma nada.
Si la ficha cambió mientras se revisaba, se exige una nueva consulta.

Esta funcionalidad requiere actualizar backend y cliente Flutter juntos. No
requiere migrar el esquema ni restaurar datos de producción.

## Revisión de productores sin número de lote

La importación admite las seis clasificaciones aunque no venga el número de
lote. Conserva el dato en `productores.clasificacion_pendiente`, sin crear una
parcela ficticia. Una clasificación vacía sigue vacía: no se deduce SISTEMA ni
SIN SISTEMA para los registros antiguos.

La API devuelve `clasificacion` y `revisionLotePendiente` en ficha y listados.
La revisión se calcula por la ausencia de una tenencia vigente con lote numerado:
aplica a todos, nuevos o antiguos, con cualquier clasificación o sin ella.
Completar/asignar el número cierra la revisión; quitar la parcela la reabre.
No es la revisión SIE y no se cierra aceptando una corrección de nombre.

Al crear la parcela se usa la clasificación pendiente si no se especifica otra;
Flutter la preselecciona para confirmarla o corregirla. Después manda la del lote.
Se mantienen la prioridad de SISTEMA para A-H, los códigos personales y los demás
requisitos de impresión. Los informes siguen incluyendo «Número de lote» como
dato faltante y el indicador `credencialLista` ahora también lo comprueba.

La columna es nullable y aditiva; `ddl-auto=update` la agrega al iniciar la nueva
versión. Alternativa para entornos con migraciones manuales:
`src/main/resources/db/productor-clasificacion-pendiente.sql` (repetible).
No reconstruye clasificaciones que importaciones antiguas hayan descartado.
Se deben actualizar backend y frontend juntos; no requiere restaurar datos.

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
