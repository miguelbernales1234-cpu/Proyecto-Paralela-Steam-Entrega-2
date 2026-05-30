package common;

import java.io.Serializable;

public class Response implements Serializable {
    private static final long serialVersionUID = 2L;

    private boolean success;
    private Object result;
    private String errorMessage;
    // Marca de reloj de Lamport del nodo que genera la respuesta
    private long lamportTime;

    // Este metodo tiene como objetivo crear una respuesta exitosa con su respectivo resultado
    public Response(boolean success, Object result) {
        this.success = success;
        this.result = result;
        this.errorMessage = null;
        this.lamportTime = 0;
    }

    // Este metodo tiene como objetivo crear una respuesta de error con su respectivo mensaje
    public Response(String errorMessage) {
        this.success = false;
        this.result = null;
        this.errorMessage = errorMessage;
        this.lamportTime = 0;
    }

    // Este metodo tiene como objetivo indicar si la respuesta fue exitosa
    public boolean isSuccess() {
        return success;
    }

    // Este metodo tiene como objetivo retornar el resultado de la respuesta
    public Object getResult() {
        return result;
    }

    // Este metodo tiene como objetivo retornar el mensaje de error si hubo alguno
    public String getErrorMessage() {
        return errorMessage;
    }

    // Este metodo tiene como objetivo obtener la marca de reloj de Lamport de esta respuesta
    public long getLamportTime() {
        return lamportTime;
    }

    // Este metodo tiene como objetivo asignar la marca de reloj de Lamport a esta respuesta
    public void setLamportTime(long lamportTime) {
        this.lamportTime = lamportTime;
    }
}

