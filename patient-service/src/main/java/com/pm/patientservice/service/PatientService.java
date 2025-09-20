package com.pm.patientservice.service;

import com.pm.patientservice.dto.PatientRequestDTO;
import com.pm.patientservice.dto.PatientResponseDTO;
import com.pm.patientservice.exceptions.EmailAlreadyExsistsException;
import com.pm.patientservice.exceptions.PatientNotFoundException;
import com.pm.patientservice.grpc.BillingServiceGrpcClient;
import com.pm.patientservice.kafka.KafkaProducer;
import com.pm.patientservice.mapper.PatientMapper;
import com.pm.patientservice.model.Patient;
import com.pm.patientservice.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);
    private PatientRepository patientRepository;
    private PatientMapper patientMapper;
    private BillingServiceGrpcClient billingServiceGrpcClient;
    private KafkaProducer kafkaProducer;

    public PatientService(PatientRepository patientRepository, PatientMapper patientMapper, BillingServiceGrpcClient billingServiceGrpcClient, KafkaProducer kafkaProducer) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
        this.kafkaProducer = kafkaProducer;
    }

    public List<PatientResponseDTO> getAllPatients(){
        List<Patient> patients = patientRepository.findAll();

        return patients.stream().map(patient -> patientMapper.toPatientResponseDTO(patient)).toList();
    }

    public PatientResponseDTO createPatient(PatientRequestDTO patientRequestDTO) {

        log.info("Patient is being created");

        if(patientRepository.existsByEmail(patientRequestDTO.getEmail())){
            throw new EmailAlreadyExsistsException("Email ID already exists" + patientRequestDTO.getEmail());
        }

        Patient patient =  patientRepository.save(patientMapper.toPatient(patientRequestDTO));

        log.info("Patient successfully created");

        //gRPC call
        billingServiceGrpcClient.createBillingAccount(patient.getId().toString(),
                patient.getName(),patient.getEmail());

        //sending to a kafka topic
        kafkaProducer.sendPatientEvent(patient);

        return patientMapper.toPatientResponseDTO(patient);

    }

    public PatientResponseDTO updatePatient(UUID id, PatientRequestDTO patientRequestDTO) {

        Patient patient = patientRepository.findById(id).orElseThrow(()-> new PatientNotFoundException("Patient not found with id:"+id));


        if(patientRepository.existsByEmailAndIdNot(patientRequestDTO.getEmail(),id)){
            throw new EmailAlreadyExsistsException("Email ID already exists" + patientRequestDTO.getEmail());
        }

        patient.setName(patientRequestDTO.getName());
        patient.setEmail(patientRequestDTO.getEmail());
        patient.setAddress(patientRequestDTO.getAddress());
        patient.setDateOfBirth(LocalDate.parse(patientRequestDTO.getDateOfBirth()));
        patientRepository.save(patient);

        return patientMapper.toPatientResponseDTO(patient);

    }

    public void deletePatient(UUID id) {
        if(patientRepository.existsById(id)) {
            patientRepository.deleteById(id);
        }
        else {
            throw new PatientNotFoundException("Patient not found with id:"+id);
        }

    }
}
