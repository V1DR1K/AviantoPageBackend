package ar.com.avianto.landing.catalogo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
class ErroresCatalogo {
    @ExceptionHandler(CatalogoService.DatosInvalidos.class)
    ProblemDetail datos(CatalogoService.DatosInvalidos error) {
        var respuesta = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
        respuesta.setProperty("campos", error.errores());
        return respuesta;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail archivoGrande() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Cada foto puede pesar hasta 6 MB.");
    }
}
