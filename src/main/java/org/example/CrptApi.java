package org.example;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

//Клиент доступа к API честного знака по следующей документации:
//https://znak-it.ru/wp-content/uploads/2022/04/api-gis-mt.pdf?ysclid=lrna8sfo9183704435
public class CrptApi {

    private final long requestLimit;
    private final TimeUnit timeUnit;
    private final AtomicLong acquired;
    private final HttpClient httpClient;
    private final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ObjectWriter objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();


    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.acquired = new AtomicLong(0);
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public void createDocument(Document document, String signature, String token, String pg) {
        while (true) {
            synchronized (this) {
                if (executor.isShutdown()) {
                    throw new IllegalStateException("CrptApi object is closed");
                }
                if (acquired.get() >= requestLimit) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    acquired.incrementAndGet();
                    executor.schedule(() -> {
                        synchronized (this) {
                            acquired.decrementAndGet();
                            this.notify();
                        }
                    }, 1, timeUnit);
                    break;
                }
            }
        }
        try {
            String encodedDocument = Base64.getEncoder().encodeToString(objectWriter.writeValueAsBytes(document));
            String encodedSignature = Base64.getEncoder().encodeToString(signature.getBytes());
            CrptApiCreateRequest createRequest = new CrptApiCreateRequest("MANUAL", "LP_INTRODUCE_GOODS",
                    encodedDocument, encodedSignature);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "?pg=" + pg))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectWriter.writeValueAsString(createRequest)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new RuntimeException(String.format("Ошибка с кодом %d. Тело возврата: %s", response.statusCode(),
                        response.body()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 2);
        AtomicLong threadsLeft = new AtomicLong(10);
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    crptApi.createDocument(new Document(), "signature", "token", "pg");
                    System.out.println("Success");
                } catch (Throwable e) {
                    System.out.println(e.getMessage());
                }
                synchronized (threadsLeft) {
                    threadsLeft.decrementAndGet();
                    threadsLeft.notify();
                }
            }).start();
        }
        while (true) {
            synchronized (threadsLeft) {
                if (threadsLeft.get() == 0) {
                    crptApi.close();
                    break;
                }
            }
        }
    }

    public synchronized void close() throws InterruptedException {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    public static class CrptApiCreateRequest {

        private String document_format;
        private String type;
        private String product_document;
        private String signature;

        public CrptApiCreateRequest(String document_format, String type, String product_document, String signature) {
            this.document_format = document_format;
            this.type = type;
            this.product_document = product_document;
            this.signature = signature;
        }

        public String getDocument_format() {
            return document_format;
        }

        public void setDocument_format(String document_format) {
            this.document_format = document_format;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getProduct_document() {
            return product_document;
        }

        public void setProduct_document(String product_document) {
            this.product_document = product_document;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

    }

    public static class Description {

        public String participantInn;

        public Description(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }
    }

    public static class Product {

        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;

        public Product(String certificate_document, String certificate_document_date,
                       String certificate_document_number, String owner_inn, String producer_inn,
                       String production_date, String tnved_code, String uit_code, String uitu_code) {
            this.certificate_document = certificate_document;
            this.certificate_document_date = certificate_document_date;
            this.certificate_document_number = certificate_document_number;
            this.owner_inn = owner_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.tnved_code = tnved_code;
            this.uit_code = uit_code;
            this.uitu_code = uitu_code;
        }


        public String getCertificate_document() {
            return certificate_document;
        }

        public void setCertificate_document(String certificate_document) {
            this.certificate_document = certificate_document;
        }

        public String getCertificate_document_date() {
            return certificate_document_date;
        }

        public void setCertificate_document_date(String certificate_document_date) {
            this.certificate_document_date = certificate_document_date;
        }

        public String getCertificate_document_number() {
            return certificate_document_number;
        }

        public void setCertificate_document_number(String certificate_document_number) {
            this.certificate_document_number = certificate_document_number;
        }

        public String getOwner_inn() {
            return owner_inn;
        }

        public void setOwner_inn(String owner_inn) {
            this.owner_inn = owner_inn;
        }

        public String getProducer_inn() {
            return producer_inn;
        }

        public void setProducer_inn(String producer_inn) {
            this.producer_inn = producer_inn;
        }

        public String getProduction_date() {
            return production_date;
        }

        public void setProduction_date(String production_date) {
            this.production_date = production_date;
        }

        public String getTnved_code() {
            return tnved_code;
        }

        public void setTnved_code(String tnved_code) {
            this.tnved_code = tnved_code;
        }

        public String getUit_code() {
            return uit_code;
        }

        public void setUit_code(String uit_code) {
            this.uit_code = uit_code;
        }

        public String getUitu_code() {
            return uitu_code;
        }

        public void setUitu_code(String uitu_code) {
            this.uitu_code = uitu_code;
        }

    }

    public static class Document {

        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;

        public Document(Description description, String doc_id, String doc_status, String doc_type,
                        boolean importRequest, String owner_inn, String participant_inn, String producer_inn,
                        String production_date, String production_type, List<Product> products, String reg_date,
                        String reg_number) {
            this.description = description;
            this.doc_id = doc_id;
            this.doc_status = doc_status;
            this.doc_type = doc_type;
            this.importRequest = importRequest;
            this.owner_inn = owner_inn;
            this.participant_inn = participant_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.production_type = production_type;
            this.products = products;
            this.reg_date = reg_date;
            this.reg_number = reg_number;
        }

        public Document() {

        }

        public Description getDescription() {
            return description;
        }

        public void setDescription(Description description) {
            this.description = description;
        }

        public String getDoc_id() {
            return doc_id;
        }

        public void setDoc_id(String doc_id) {
            this.doc_id = doc_id;
        }

        public String getDoc_status() {
            return doc_status;
        }

        public void setDoc_status(String doc_status) {
            this.doc_status = doc_status;
        }

        public String getDoc_type() {
            return doc_type;
        }

        public void setDoc_type(String doc_type) {
            this.doc_type = doc_type;
        }

        public boolean isImportRequest() {
            return importRequest;
        }

        public void setImportRequest(boolean importRequest) {
            this.importRequest = importRequest;
        }

        public String getOwner_inn() {
            return owner_inn;
        }

        public void setOwner_inn(String owner_inn) {
            this.owner_inn = owner_inn;
        }

        public String getParticipant_inn() {
            return participant_inn;
        }

        public void setParticipant_inn(String participant_inn) {
            this.participant_inn = participant_inn;
        }

        public String getProducer_inn() {
            return producer_inn;
        }

        public void setProducer_inn(String producer_inn) {
            this.producer_inn = producer_inn;
        }

        public String getProduction_date() {
            return production_date;
        }

        public void setProduction_date(String production_date) {
            this.production_date = production_date;
        }

        public String getProduction_type() {
            return production_type;
        }

        public void setProduction_type(String production_type) {
            this.production_type = production_type;
        }

        public List<Product> getProducts() {
            return products;
        }

        public void setProducts(List<Product> products) {
            this.products = products;
        }

        public String getReg_date() {
            return reg_date;
        }

        public void setReg_date(String reg_date) {
            this.reg_date = reg_date;
        }

        public String getReg_number() {
            return reg_number;
        }

        public void setReg_number(String reg_number) {
            this.reg_number = reg_number;
        }

    }

}