// Marque de l'école sur les pages de connexion : nom, ville, phrase d'accueil et logo, lus dans l'API
// publique (GET /api/v1/public/marque) pour l'école de l'application qui demande la connexion.
//
// Sans la marque (API injoignable, école inconnue, hôte commun), la page reste utilisable : on garde
// le texte neutre « Plateforme de gestion scolaire ». Rien n'est jamais écrit en HTML : uniquement
// textContent et src (aucune injection possible depuis une valeur saisie par une école).
(function () {
  "use strict";

  function init() {
    var aside = document.getElementById("ecole-marque");
    if (!aside) {
      return;
    }
    var api = (aside.getAttribute("data-api") || "").replace(/\/+$/, "");
    if (!/^https?:\/\//.test(api)) {
      return;
    }

    /** Adresse de l'application qui a demandé la connexion (redirect_uri, ou client_data après erreur). */
    function adresseApplication() {
      var params = new URLSearchParams(window.location.search);
      var ru = params.get("redirect_uri");
      if (!ru) {
        var donnees = params.get("client_data");
        if (donnees) {
          try {
            var base64 = donnees.replace(/-/g, "+").replace(/_/g, "/");
            ru = JSON.parse(atob(base64)).ru;
          } catch (e) {
            ru = null;
          }
        }
      }
      try {
        return ru ? new URL(ru) : null;
      } catch (e) {
        return null;
      }
    }

    var application = adresseApplication();
    var requete = new URLSearchParams();
    if (application) {
      requete.set("hote", application.host);
      // Développement : le lien de l'école peut porter son identifiant (?ecole=<uuid>).
      var ecole = application.searchParams.get("ecole");
      if (
        ecole &&
        /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
          ecole,
        )
      ) {
        requete.set("ecole", ecole);
      }
    }
    var chaine = requete.toString();

    function montrer(id, texte) {
      var element = document.getElementById(id);
      if (element && texte) {
        element.textContent = texte;
        element.hidden = false;
      }
      return element;
    }

    fetch(api + "/api/v1/public/marque" + (chaine ? "?" + chaine : ""), {
      method: "GET",
      mode: "cors",
      credentials: "omit",
      headers: { Accept: "application/json" },
    })
      .then(function (reponse) {
        return reponse.ok ? reponse.json() : null;
      })
      .then(function (marque) {
        if (!marque || !marque.nom) {
          return;
        }
        montrer("ecole-nom", marque.nom);
        montrer("ecole-ville", marque.ville);
        montrer("ecole-phrase", marque.phraseAccueil);
        var plateforme = document.getElementById("ecole-plateforme");
        if (plateforme) {
          plateforme.classList.add("ecole-plateforme--discrete");
        }
        var logo = document.getElementById("ecole-logo");
        if (logo && marque.logoVersion) {
          logo.addEventListener("load", function () {
            logo.hidden = false;
            aside.classList.add("ecole-marque--avec-logo");
          });
          logo.src =
            api +
            "/api/v1/public/marque/logo?" +
            (chaine ? chaine + "&" : "") +
            "v=" +
            encodeURIComponent(marque.logoVersion);
        }
        aside.classList.add("ecole-marque--ecole");
        // Le nom de l'école ouvre aussi le titre de l'onglet : on sait où l'on se connecte.
        if (document.title.indexOf(marque.nom) === -1) {
          document.title = marque.nom + " – " + document.title;
        }
      })
      .catch(function () {
        /* marque neutre : rien à faire */
      });
  }

  // Le script est chargé dans l'en-tête : on attend que la page soit construite.
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
