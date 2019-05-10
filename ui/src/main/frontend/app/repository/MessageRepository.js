const SAY_HELLO_TO = (name) => `/hello-service/hello/${name}`;
const SAVE_A_NEW_MESSAGE = "/message-service/message";
const DELETE_A_MESSAGE = (messageId) => `/message-service/message/${messageId}`;

export default class MessageRepository {

    sayHelloTo(name) {
        return fetch(SAY_HELLO_TO(name))
            .then(data => data.text());
    }

    saveMessage(message) {
        return fetch(SAVE_A_NEW_MESSAGE, {
            method: "POST",
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(message),
            credentials: 'same-origin'
        })
    }

    findMessages() {
        return fetch(SAVE_A_NEW_MESSAGE, {
            method: "GET",
            headers: {
                'Accept': 'application/json'
            },
            credentials: 'same-origin'
        }).then(response => response.json())
    }


    deleteMessage(messageId) {
        return fetch(DELETE_A_MESSAGE(messageId), {
            method: "DELETE",
            credentials: 'same-origin'
        })
    }
}