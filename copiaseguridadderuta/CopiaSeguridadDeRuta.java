package com.mycompany.copiaseguridadderuta;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;

/**
 * Aplicación para hacer una copia de seguridad de los datos que hay en deRuta.
 * Se guarda en un XML en la carpeta indicada.
 *
 * @author josef
 */
public class CopiaSeguridadDeRuta {

    private static String carpetaDestino = Configuracion.CARPETA_BACKUP;

    public static void main(String[] args) {
        obtenerDatos();
    }

    private static void obtenerDatos() {
        try {
            Map<String, List<JSONObject>> backup = obtenerBackup();

            JSONObject backupJson = new JSONObject();
            for (Map.Entry<String, List<JSONObject>> entry : backup.entrySet()) {
                backupJson.put(entry.getKey(), entry.getValue());
            }

            // convierte JSONObject a XML
            String xmlFormateado = XML.toString(backupJson);

            File carpeta = new File(carpetaDestino);
            if (!carpeta.exists()) {
                carpeta.mkdirs();
            }

            File archivo = new File(carpeta, "backup_deRuta.xml");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(archivo))) {
                String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
                writer.write("<!-- Copia creada: " + fecha + " -->\n");
                writer.write(xmlFormateado);
            }

            System.out.println("Backup XML guardado en: " + archivo.getAbsolutePath());

        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Consulta en mysql los grupos que hay y luego en firestore descarga los
     * datos de todos esos grupos.
     *
     * @return
     * @throws IOException
     * @throws JSONException
     */
    public static Map<String, List<JSONObject>> obtenerBackup() throws IOException, JSONException {
        Map<String, List<JSONObject>> backup = new HashMap<>();

        // 1. Hacemos la petición para obtener todos los grupos directamente
        String respuestaGrupos = enviarPost(Configuracion.URL_TODOS_GRUPOS, new JSONObject().toString());
        JSONArray grupos = new JSONArray(respuestaGrupos);

        // 2. Iteramos sobre cada grupo
        for (int i = 0; i < grupos.length(); i++) {
            String grupo = grupos.getJSONObject(i).getString("nombre");

            JSONObject body = new JSONObject();
            body.put("grupo", grupo);

            String respuestaDatos = enviarPost(Configuracion.URL_OBTENER_DATOS, body.toString());
            JSONObject response = new JSONObject(respuestaDatos);

            if (response.getBoolean("success")) {
                JSONArray datos = response.getJSONArray("data");
                List<JSONObject> listaDatos = new ArrayList<>();
                for (int j = 0; j < datos.length(); j++) {
                    listaDatos.add(datos.getJSONObject(j));
                }
                backup.put(grupo, listaDatos);
            }
        }

        return backup;
    }

    /**
     * Método par la conexión al servidor.
     *
     * @param urlString
     * @param jsonInput
     * @return
     * @throws IOException
     */
    private static String enviarPost(String urlString, String jsonInput) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonInput.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"));
        StringBuilder response = new StringBuilder();
        String responseLine;

        while ((responseLine = br.readLine()) != null) {
            response.append(responseLine.trim());
        }

        return response.toString();
    }
}
