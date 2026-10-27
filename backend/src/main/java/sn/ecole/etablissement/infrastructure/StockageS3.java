package sn.ecole.etablissement.infrastructure;

import java.net.URI;
import java.time.Duration;
import org.springframework.stereotype.Component;
import sn.ecole.etablissement.domain.StockageFichiers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Stockage MinIO (API S3). Les clients sont créés au premier usage : l'application démarre sans
 * MinIO ni clés d'accès, et ne les exige que pour manipuler un fichier.
 */
@Component
class StockageS3 implements StockageFichiers {

  private final StockageProperties proprietes;
  private volatile S3Client client;
  private volatile S3Presigner presigner;
  private volatile boolean bucketPret;

  StockageS3(StockageProperties proprietes) {
    this.proprietes = proprietes;
  }

  @Override
  public void deposer(String cle, byte[] contenu, String contentType) {
    assurerBucket();
    client()
        .putObject(
            PutObjectRequest.builder()
                .bucket(proprietes.bucket())
                .key(cle)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(contenu));
  }

  @Override
  public void supprimer(String cle) {
    client()
        .deleteObject(DeleteObjectRequest.builder().bucket(proprietes.bucket()).key(cle).build());
  }

  @Override
  public URI urlSignee(String cle, Duration validite) {
    GetObjectRequest objet =
        GetObjectRequest.builder().bucket(proprietes.bucket()).key(cle).build();
    return URI.create(
        presigner()
            .presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(validite)
                    .getObjectRequest(objet)
                    .build())
            .url()
            .toString());
  }

  private void assurerBucket() {
    if (bucketPret) {
      return;
    }
    try {
      client().headBucket(HeadBucketRequest.builder().bucket(proprietes.bucket()).build());
    } catch (NoSuchBucketException e) {
      if (!proprietes.creerBucket()) {
        throw new IllegalStateException("Le bucket " + proprietes.bucket() + " n'existe pas.", e);
      }
      client().createBucket(CreateBucketRequest.builder().bucket(proprietes.bucket()).build());
    }
    bucketPret = true;
  }

  private S3Client client() {
    S3Client courant = client;
    if (courant == null) {
      synchronized (this) {
        if (client == null) {
          client =
              S3Client.builder()
                  .endpointOverride(proprietes.endpoint())
                  .region(Region.of(proprietes.region()))
                  .credentialsProvider(identifiants())
                  .serviceConfiguration(
                      S3Configuration.builder().pathStyleAccessEnabled(true).build())
                  .build();
        }
        courant = client;
      }
    }
    return courant;
  }

  private S3Presigner presigner() {
    S3Presigner courant = presigner;
    if (courant == null) {
      synchronized (this) {
        if (presigner == null) {
          presigner =
              S3Presigner.builder()
                  .endpointOverride(proprietes.endpointPourUrlSignee())
                  .region(Region.of(proprietes.region()))
                  .credentialsProvider(identifiants())
                  .serviceConfiguration(
                      S3Configuration.builder().pathStyleAccessEnabled(true).build())
                  .build();
        }
        courant = presigner;
      }
    }
    return courant;
  }

  private StaticCredentialsProvider identifiants() {
    if (proprietes.accessKey() == null
        || proprietes.accessKey().isBlank()
        || proprietes.secretKey() == null
        || proprietes.secretKey().isBlank()) {
      throw new IllegalStateException(
          "Stockage non configuré : ecole.stockage.access-key et secret-key sont requis.");
    }
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(proprietes.accessKey(), proprietes.secretKey()));
  }
}
