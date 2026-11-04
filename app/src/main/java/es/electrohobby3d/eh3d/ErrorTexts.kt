// EH3D-ANDROID-CONTROL - the text for each refusal code of the server that the app can meet, in the language of the app.
// The codes are the ones of docs/error-codes.json of EH3D-SERVER; the tests of the app check that no code of that catalog is left without a text or a reason.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

object ErrorTexts {
    /** The words for a refusal code, or null when the app has none and shows the message of the server. */
    fun phrase(code: String): String? = when (code) {
        "bad_address" -> tr("Un nodo necesita su dirección (host:puerto).")
        "bad_cfg" -> tr("La configuración tiene una clave que no existe o un valor fuera de rango.")
        "bad_days" -> tr("Los días deben ser un número de 1 a 3650.")
        "bad_gcode" -> tr("El programa tiene una línea que no es G-code: los ajustes y órdenes en tiempo real del controlador (\$, !, ~, ?), los caracteres de control y las líneas de más de 200 caracteres se rechazan.")
        "bad_id" -> tr("El id debe ser letras minúsculas, dígitos y guiones, hasta 32 caracteres.")
        "bad_light" -> tr("Al anillo de LEDs se le pidió algo que el nodo no usa (un nivel, un tono o sectores que no conoce).")
        "bad_request" -> tr("El servidor no aceptó lo enviado.")
        "bad_station" -> tr("La estación debe ser letras minúsculas, dígitos y guiones, hasta 32 caracteres.")
        "forbidden" -> tr("Tu rol no permite esto.")
        "head_refused" -> tr("El nodo del cabezal de taladro rechazó la petición (comprueba su token en la configuración de la máquina).")
        "internal" -> tr("El servidor falló con esta petición. Inténtelo de nuevo.")
        "job_active" -> tr("El trabajo está en marcha: páralo primero.")
        "last_admin" -> tr("El último administrador no se puede borrar, desactivar ni degradar.")
        "local_only" -> tr("Este servidor aún no tiene usuarios: cree el administrador desde el ordenador en el que funciona.")
        "machine_exists" -> tr("Ya existe una máquina con ese id.")
        "machines_busy" -> tr("Hay un trabajo en marcha: actualizar reinicia el servidor y lo detendría. Espera a que termine.")
        "not_cancellable" -> tr("El trabajo no se puede cancelar en su estado actual.")
        "not_ready" -> tr("El trabajo no está listo para empezar (puede haberse cancelado o empezado ya).")
        "not_running" -> tr("El trabajo no está en marcha.")
        "out_of_table" -> tr("El trabajo sale de la mesa de la máquina.")
        "storage" -> tr("El servidor no pudo escribir sus datos. Revise el disco.")
        "too_big" -> tr("El trabajo es demasiado grande (más de un millón de líneas, o más de lo que admite el servidor).")
        "unauthorized" -> tr("El nombre o la contraseña no son correctos, o la sesión ha terminado.")
        "unknown_job" -> tr("Ese trabajo no existe.")
        "unknown_machine" -> tr("Esa máquina no existe.")
        "unknown_token" -> tr("No existe ese token.")
        "unknown_user" -> tr("Ese usuario no existe.")
        "update_refused" -> tr("El asistente de actualización rechazó la petición.")
        "updates_off" -> tr("Las actualizaciones no están configuradas en este servidor.")
        "user_exists" -> tr("Ese usuario ya existe.")
        "wrong_machine" -> tr("Esta máquina no admite este tipo de trabajo: un nodo admite taladrado y fresado, una máquina GRBL admite G-code y el simulador admite los tres.")
        else -> null
    }
}
